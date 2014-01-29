package wcNaver.webtoon;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.helper.Validate;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * This class provides utility methods to crawl naver webtoon
 * TODO: Replace all pw.format() messages with logging
 */
public class NaverWebtoonCrawler {

    private static PrintWriter pw = new PrintWriter(System.out, true);


    /**
     * Download available webtoon information for given day
     *
     * @param day the day you want to download webtoon list from
     * @return Available webtoon information for the given day
     */
    public static NaverWebtoonInfo[] downloadWebtoonListByDay(Day day) {
        // Create necessary references
        NaverWebtoonInfo[] info;
        String href, titleId, url = "";
        Document doc;
        Element content, img, link;
        Elements imgList;
        int webtoonTotal; // total number of webtoons for this day
        Matcher mat;

        // Try connect to url.
        try {
            url = NaverWebtoonURL.getDayListURL(day);
            doc = Jsoup.connect(url).get();
        } catch (IOException e) {
            pw.println("Unable to connect to " + url);
            e.printStackTrace();
            return null;
        }

        // Grab available webtoon lists
        content = doc.getElementById("content");
        imgList = content.getElementsByClass("img_list").first().children();
        webtoonTotal = imgList.size();

        info = new NaverWebtoonInfo[webtoonTotal];


        for (int i = 0; i < webtoonTotal; i++) {
            img = imgList.get(i);
            link = img.getElementsByClass("thumb").first()
                    .getElementsByTag("a").first();
            href = link.attr("href");

            String thumbURL = link.child(0).absUrl("src");
            // Use Regex to pull title id from the href link
            mat = NaverWebtoonURL.titleIdPat.matcher(href);
            mat.find();
            try {
                info[i] = new NaverWebtoonInfo(mat.group(1), link.attr("title"),
                        new ImageIcon(new URL(thumbURL)));
            } catch (MalformedURLException e) {
                pw.println("Unable to download thumbnail from url " + thumbURL);
                e.printStackTrace();
            }
        }

        return info;
    }

}
