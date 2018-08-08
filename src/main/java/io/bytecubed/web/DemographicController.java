package io.bytecubed.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.UnexpectedPage;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            UnexpectedPage stuff = client.getPage("https://factfinder.census.gov/tablerestful/tableServices/renderProductData?renderForMap=f&renderForChart=f&src=CF&log=t&_ts=55173673748");

            String theString = IOUtils.toString(stuff.getInputStream());
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
            String[] tokens = text.replace(",","").split(" ");
            int censusAge =8;
            int lower =  Integer.parseInt(tokens[0].trim()) + censusAge;
            int upper = Integer.parseInt(tokens[2].trim()) + censusAge;
            int count = Integer.parseInt(tokens[4].trim());
            double percentage = Double.parseDouble(tokens[5].trim());

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
                    .filter(f -> f.getLower() >= 22 && f.getUpper() <= 37)
                    .mapToInt(AgeGroup::getCount)
                    .sum();

            int genXSum = ageGroups.stream()
                    .filter(f -> f.getLower() >= 38 && f.getUpper() <= 53)
                    .mapToInt(AgeGroup::getCount)
                    .sum();

            int boomers = ageGroups.stream()
                    .filter(f -> f.getLower() >= 54 && f.getUpper() <= 72)
                    .mapToInt(AgeGroup::getCount)
                    .sum();

            generations.add( new Generation( "Millennial", melinialSum, ((double)melinialSum/total)));
            generations.add( new Generation( "Generation X", genXSum, ((double)genXSum/total)));
            generations.add( new Generation( "Boomers", boomers, ((double)boomers/total)));
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
