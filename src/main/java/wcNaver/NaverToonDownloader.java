package wcNaver;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import wcNaver.bestChallenge.NaverBCURL;
import wcNaver.challenge.NaverCHURL;
import wcNaver.webtoon.NaverWebtoonURL;

import javax.swing.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;

import static wcNaver.NaverURL.LOGIN_FORM;

/**
 * This class download webtoon, and
 * implements thread.
 */
public class NaverToonDownloader implements Runnable
{

    private static final int     MAX_DOWNLOAD_SIZE_BYTES = 1024 * 1024 * 10; // 10MB
    private              boolean shutdown                = false;
    private              boolean pause                   = false;
    private NaverToonInfo info;
    private JProgressBar  totalProg;
    private JProgressBar  partialProg;
    private PrintWriter pw = new PrintWriter(System.out, true);
    private Thread            thread;
    private Path              saveDir;
    private NaverToonCategory cat;
    private JLabel            wtMsgLabel; // displays where Toons are saved

    final private static String pattern = "[\\/:*?\"<>|]";

    public NaverToonDownloader(NaverToonInfo info,
                               JProgressBar totalProg,
                               JProgressBar partialProg,
                               JLabel saveDirLabel)
    {
        this.info = info;
        this.totalProg = totalProg;
        this.partialProg = partialProg;
        this.cat = info.getCategory();
        this.wtMsgLabel = saveDirLabel;
        thread = new Thread(this);
    }

    public void setSaveDir(Path saveDir)
    {
        this.saveDir = saveDir;
    }

    public synchronized void shutdown()
    {
        shutdown = true;
        pause = false;
        notify();
    }

    public synchronized void pause()
    {
        pause = true;
    }

    public synchronized void resume()
    {
        pause = false;
        notify();
    }

    public synchronized boolean isPaused()
    {
        return pause;
    }

    public synchronized void start()
    {
        thread.start();
    }

    @Override
    public void run()
    {

        String              wtListURL = ""; // The url that lists all available webtoon series
        String              wtURL     = "", imgURL, wtSeriesName;
        String              wtFileName;
        Element             wtList    = null, wtPage, wtViewer;
        Matcher             wtTotalMatcher;
        Connection.Response wtRes;
        FileOutputStream    wtOut;
        int                 wtTotal; //The total number of available webtoon series
        int                 wtCount;
        double              totalInc;
        double              partialInc;

        Path base = null, wtDir = null, wtSeriesDir;

        // Figure out the number of total series
        if (cat == NaverToonCategory.WEBTOON)
        {
            wtListURL = NaverWebtoonURL.getWebtoonListURL(info.getTitleId());
        }
        else if (cat == NaverToonCategory.BEST)
        {
            wtListURL = NaverBCURL.getBCListURL(info.getTitleId());
        }
        else
        {
            wtListURL = NaverCHURL.getChListURL(info.getTitleId());
        }

        try
        {
            wtList = Jsoup.connect(wtListURL).get();
        }
        catch (IOException e)
        {
            pw.println("Unable to connect to " + wtListURL);
            e.printStackTrace();
        }

        String href = wtList.getElementById("content")
                .getElementsByClass("title").first()
                .getElementsByTag("a").first().attr("href");

        // Use Regex to pull the total number of series from the href link
        wtTotalMatcher = NaverWebtoonURL.noPat.matcher(href);
        wtTotalMatcher.find();
        wtTotal = Integer.parseInt(wtTotalMatcher.group(1));

        totalInc = (totalProg.getMaximum() - totalProg.getMinimum()) /
                (double) wtTotal;

        try
        {
            /*
            TODO: saveDir.resolve doesn't work and causes this method to
             terminate when this application is run by double-click
             in Mac OS. It works find in Windows. Figure out what is
             wrong when I have time.
             */
            if (cat == NaverToonCategory.WEBTOON)
            {
                base = saveDir.resolve("네이버 웹툰"); // get the base directory
            }
            else if (cat == NaverToonCategory.BEST)
            {
                base = saveDir.resolve("베스트 도전");
            }
            else
            {
                base = saveDir.resolve("도전만화");
            }

            if (!Files.exists(base)) Files.createDirectory(base);

            wtDir = base.resolve(
                    getValidName(info.getTitleName(), info.getTitleId())
            ); // create the webtoon directory
            if (!Files.exists(wtDir)) Files.createDirectory(wtDir);

            // Display where the webtoon is saved
            wtMsgLabel.setText("저장위치: " + wtDir.toAbsolutePath());

        }
        catch (IOException ioe)
        {
            ioe.printStackTrace();
        }

        // Go through each series and download them
        for (int curSeries = 1; curSeries <= wtTotal; curSeries++)
        {
            // display the total progress
            totalProg.setValue((int) (totalInc * (curSeries - 1)));

            if (cat == NaverToonCategory.WEBTOON)
            {
                wtURL = NaverWebtoonURL.getWebtoonDetailURL(info.getTitleId(), curSeries);
            }
            else if (cat == NaverToonCategory.BEST)
            {
                wtURL = NaverBCURL.getBCDetailURL(info.getTitleId(), curSeries);
            }
            else
            {
                wtURL = NaverCHURL.getChDetailURL(info.getTitleId(), curSeries);
            }


            try
            {
                /**
                 * 페이지를 조회 할때 유료 페이지 일때 로그인 쿠기를 추가하여
                 * 해당 페이지를 get한다.
                 */
                wtPage = Jsoup.connect(wtURL)
                        .cookies(LOGIN_FORM.cookies())
                        .get();
                wtSeriesName = wtPage.getElementsByClass("tit_area").first()
                        .getElementsByClass("view").first()
                        .getElementsByTag("h3").first().ownText();
                wtSeriesDir = wtDir.resolve(
                        getValidName(wtSeriesName, Integer.toString(curSeries))
                );


                // If the folder exist, I assume that the contents have been
                // already downloaded
                if (Files.exists(wtSeriesDir))
                {
                    continue;
                }

                // Create the folder
                Files.createDirectory(wtSeriesDir);

                // download images
                wtCount = 1;
                wtViewer = wtPage.getElementsByClass("wt_viewer").first();
                partialInc =
                        (partialProg.getMaximum() - partialProg.getMinimum())
                                / (double) wtViewer.children().size();

                int ct = 0;
                // Display what is being downloaded
                partialProg.setString(wtSeriesName);
                for (Element imgLink : wtViewer.children())
                {

                    // Use synchronized block to check exit condition
                    synchronized (this)
                    {
                        while (pause) try
                        {
                            wait();
                        }
                        catch (InterruptedException e)
                        {
                            pw.println("Oh!? Spurious wake-up?");
                            e.printStackTrace();
                        }

                        if (shutdown) break;
                    }

                    // display the partial progress
                    partialProg.setValue((int) (partialInc * (ct++)));

                    imgURL = imgLink.absUrl("src");

                    // Check that imgURL is actually an image file
                    if (imgURL == null || !imgURL.endsWith(".jpg"))
                        continue;

                    /**
                     * 페이지를 조회 할때 유료 페이지 일때 로그인 쿠기를 추가하여
                     * 해당 페이지를 get한다.
                     */
                    wtRes = Jsoup.connect(imgURL).referrer(wtURL)
                            .ignoreContentType(true)
                            .maxBodySize(MAX_DOWNLOAD_SIZE_BYTES)
                            .cookies(LOGIN_FORM.cookies())
                            .execute();
                    wtFileName = "Image_" + (wtCount++) + ".jpg";
                    wtOut = new FileOutputStream(wtSeriesDir.resolve(wtFileName).toFile());

                    // save it!
                    wtOut.write(wtRes.bodyAsBytes());
                    wtOut.close();
                }

                // Use synchronized block to check exit condition
                synchronized (this)
                {
                    while (pause) try
                    {
                        wait();
                    }
                    catch (InterruptedException e)
                    {
                        pw.println("Oh!? Spurious wake-up?");
                        e.printStackTrace();
                    }

                    if (shutdown) break;
                    else
                    {
                        // Show that the current download is complete
                        partialProg.setValue(partialProg.getMaximum());
                    }
                }

            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }


        synchronized (this)
        {
            // If the program was not abruptly shutdown,
            // show that the download is complete.
            if (!shutdown) totalProg.setValue(totalProg.getMaximum());
        }


    }

    private String getValidName(String name, String titleId)
    {
        String validName = name.replaceAll(pattern, "");
        if (validName.length() == 0) return "이름강제변환_" + titleId;
        return validName;
    }

}
