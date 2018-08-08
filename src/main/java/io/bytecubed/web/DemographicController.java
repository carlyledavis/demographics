package io.bytecubed.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.UnexpectedPage;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HTMLParser;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.springframework.http.ResponseEntity.ok;

@RestController
public class DemographicController {

    @RequestMapping("/demographics/{zipCode}")
    public ResponseEntity<AgeGroupRepsonse> getDemographicsByZipCode(@PathVariable String zipCode) throws IOException, KeyManagementException, NoSuchAlgorithmException {
        try {
            WebClient client = new WebClient(BrowserVersion.EDGE);
            client.getOptions().setThrowExceptionOnScriptError(false);
            final HtmlPage page = client.getPage("https://factfinder.census.gov/bkmk/table/1.0/en/DEC/10_DP/DPDP1/8600000US" + zipCode);
            client.getOptions().setThrowExceptionOnScriptError(false);

            System.out.println(client.getCookieManager().getCookies());
            UnexpectedPage stuff = client.getPage("https://factfinder.census.gov/tablerestful/tableServices/renderProductData?renderForMap=f&renderForChart=f&src=CF&log=t&_ts=55173673748");

            String theString = IOUtils.toString(stuff.getInputStream());
//            System.out.println(page.getBody().asXml());
//            System.out.println(theString);
            ObjectMapper mapper = new ObjectMapper();
            Map<String, LinkedHashMap> res = mapper.readValue(theString, Map.class);
            String response = res.get("ProductData").get("productDataTable").toString();


            Document doc  = Jsoup.parse(response);
            List<AgeGroup> ageGroups = new ArrayList();
            doc.getElementsByTag("tr").stream()
                    .filter(f->f.text().trim().length()>0)
                    .filter(f->f.text().contains("to"))
                    .filter(f->asList("1","2","3","4","5","6","7","8","9").contains(f.text().substring(0,1)))
                    .forEach( f-> ageGroups.add(AgeGroup.parse(f.text())));

            return ok(new AgeGroupRepsonse(ageGroups.subList(0,15)));
//
//            asList(response.split("<tbody>")).forEach(a->{
//                System.out.println( "new:  " + a.replace("</tbody>",""));
//                try {
//                    DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
//                    builder.parse( new ByteArrayInputStream( a.getBytes() ));
//                } catch (SAXException e) {
//                    System.out.println( "This is where the exception is" );
//                    System.out.println( a );
//                    System.out.println( "This is the end of the exception." );
//                    e.printStackTrace();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                } catch (ParserConfigurationException e) {
//                    e.printStackTrace();
//                }
//            });
//            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
//            builder.parse( new ByteArrayInputStream( res.get("ProductData").get("productDataTable").toString().getBytes() ));
//            System.out.println(((LinkedHashMap)res.get("ProductData")).keySet());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ok(null);

    }

    public static class AgeGroup{

        private final int lower;
        private final int upper;
        private final int count;
        private final double percentage;

        public AgeGroup(int lower, int upper, int count, double percentage) {
            this.lower = lower;
            this.upper = upper;
            this.count = count;
            this.percentage = percentage;
        }

        public static AgeGroup parse(String text) {
            System.out.println( "row:  " + text );

            String[] tokens = text.replace(",","").split(" ");
            int lower =  Integer.parseInt(tokens[0].trim());
            int upper = Integer.parseInt(tokens[2].trim());
            int count = Integer.parseInt(tokens[4].trim());
            double percentage = Double.parseDouble(tokens[5].trim());

            System.out.println( lower );
            System.out.println( upper );
            System.out.println( count );
            System.out.println( percentage );

            return new AgeGroup(lower, upper, count, percentage );
        }

        public int getLower() {
            return lower;
        }

        public int getUpper() {
            return upper;
        }

        public int getCount() {
            return count;
        }

        public double getPercentage() {
            return percentage;
        }
    }

    public class AgeGroupRepsonse {
        private List<AgeGroup> ageGroups;
        private List<Generation> generations;

        public List<AgeGroup> getAgeGroups() {
            return ageGroups;
        }

        public List<Generation> getGenerations() {
            return generations;
        }

        public AgeGroupRepsonse(List<AgeGroup> ageGroups) {
            this.ageGroups = ageGroups;
            generations = new ArrayList<>();

            createGenerations(ageGroups);
        }

        private void createGenerations(List<AgeGroup> ageGroups) {
            int total = ageGroups.stream().mapToInt(AgeGroup::getCount).sum();
            int melinialSum = ageGroups.stream()
                    .filter(f -> f.getLower() > 20 && f.getUpper() < 30)
                    .mapToInt(AgeGroup::getCount)
                    .sum();
            generations.add( new Generation( "Mellinial", melinialSum, ((double)melinialSum/total)));
        }

        public class Generation{
            private String name;
            private int count;
            private double percentage;

            public String getName() {
                return name;
            }

            public int getCount() {
                return count;
            }

            public double getPercentage() {
                return percentage;
            }

            public Generation(String name, int count, double percentage) {

                this.name = name;
                this.count = count;
                this.percentage = percentage * 100;
            }
        }
    }
}
