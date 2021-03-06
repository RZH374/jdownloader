//    jDownloader - Downloadmanager
//    Copyright (C) 2009  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.
package jd.plugins.hoster;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;

import org.appwork.storage.config.annotations.DefaultBooleanValue;
import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.TimeFormatter;
import org.jdownloader.downloader.hds.HDSDownloader;
import org.jdownloader.downloader.hls.HLSDownloader;
import org.jdownloader.plugins.components.config.MediathekProperties;
import org.jdownloader.plugins.components.hls.HlsContainer;
import org.jdownloader.plugins.config.Order;
import org.jdownloader.plugins.config.PluginConfigInterface;
import org.jdownloader.plugins.config.PluginJsonConfig;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.FilePackage;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.MediathekHelper;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "tvnow.de" }, urls = { "tvnowdecrypted://.+" })
public class TvnowDe extends PluginForHost {
    public TvnowDe(final PluginWrapper wrapper) {
        super(wrapper);
    }

    /* Settings */
    /* Tags: rtl-interactive.de, RTL, rtlnow, rtl-now */
    private static final String           TYPE_GENERAL_ALRIGHT           = "https?://[^/]+/[^/]+/[a-z0-9\\-]+/[^/\\?]+";
    /* Old + new movie-linktype */
    public static final String            TYPE_MOVIE_OLD                 = "https?://[^/]+/[^/]+/[^/]+";
    public static final String            TYPE_MOVIE_NEW                 = "https?://[^/]+/filme/.+";
    public static final String            TYPE_SERIES_NEW                = "https?://[^/]+/serien/([^/]+)$";
    public static final String            TYPE_SERIES_SINGLE_EPISODE_NEW = "https?://[^/]+/(?:serien|shows)/([^/]+)/(?:[^/]+/)?([^/]+)";
    public static final String            TYPE_DEEPLINK                  = "^[a-z]+://link\\.[^/]+/.+";
    public static final String            API_BASE                       = "https://api.tvnow.de/v3";
    public static final String            CURRENT_DOMAIN                 = "tvnow.de";
    private LinkedHashMap<String, Object> entries                        = null;

    public static Browser prepBR(final Browser br) {
        br.getHeaders().put("Accept", "application/json, text/plain, */*");
        /* 400-bad request for invalid API requests */
        br.setAllowedResponseCodes(new int[] { 400 });
        br.setFollowRedirects(false);
        return br;
    }

    public static boolean isMovie(final String url) {
        return url.matches(TYPE_MOVIE_OLD) || url.matches(TYPE_MOVIE_NEW);
    }

    public static boolean isMovie_old(final String url) {
        return url.matches(TYPE_MOVIE_OLD) && !url.matches(TYPE_MOVIE_NEW) && !url.matches(TYPE_SERIES_SINGLE_EPISODE_NEW) && !url.matches(TYPE_SERIES_NEW);
    }

    @Override
    public void correctDownloadLink(final DownloadLink link) throws Exception {
        /* First lets get our source url and remove the unneeded parts */
        String urlNew;
        if (link.getPluginPatternMatcher().matches(TYPE_DEEPLINK)) {
            /* https not possible for this linktype!! */
            urlNew = link.getPluginPatternMatcher().replaceAll("tvnowdecrypted://", "http://");
        } else {
            urlNew = link.getPluginPatternMatcher().replaceAll("tvnowdecrypted://", "https://");
        }
        link.setPluginPatternMatcher(urlNew);
    }

    /**
     * ~2015-05-01 Available HLS AND HDS streams are DRM protected <br />
     * ~2015-07-01: HLS streams were turned off <br />
     * ~2016-01-01: RTMP(E) streams were turned off / all of them are DRM protected/crypted now<br />
     * ~2016-02-24: Summary: There is absolutely NO WAY to download from this website <br />
     * ~2016-03-15: Domainchange from nowtv.de to tvnow.de<br />
     * .2018-04-17: Big code cleanup and HLS streams were re-introduced<br />
     */
    @SuppressWarnings({ "unchecked" })
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws Exception {
        setBrowserExclusive();
        /* Fix old urls */
        correctDownloadLink(downloadLink);
        prepBR(this.br);
        /* Required to access items via API and also used as linkID */
        final String urlpart = getURLPart(downloadLink);
        // ?fields=*,format,files,manifest,breakpoints,paymentPaytypes,trailers,packages,isDrm
        /*
         * Explanation of possible but left-out parameters: "breakpoints" = timecodes when ads are delivered, "paymentPaytypes" = how can
         * this item be purchased and how much does it cost, "trailers" = trailers, "files" = old rtlnow URLs, see plugin revision 38232 and
         * earlier
         */
        br.getPage(API_BASE + "/movies/" + urlpart + "?fields=" + getFields());
        if (br.getHttpConnection().getResponseCode() != 200) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaObject(br.toString());
        final LinkedHashMap<String, Object> format = (LinkedHashMap<String, Object>) entries.get("format");
        final String tv_station = (String) format.get("station");
        final String formatTitle = (String) format.get("title");
        return parseInformation(downloadLink, entries, tv_station, formatTitle);
    }

    /** Returns parameters for API 'fields=' key. Only request all fields we actually need. */
    public static String getFields() {
        return "*,format,packages,isDrm";
    }

    public static AvailableStatus parseInformation(final DownloadLink downloadLink, final LinkedHashMap<String, Object> entries, final String tv_station, final String formatTitle) {
        final MediathekProperties data = downloadLink.bindData(MediathekProperties.class);
        final String date = (String) entries.get("broadcastStartDate");
        final String episode_str = new Regex(downloadLink.getPluginPatternMatcher(), "folge\\-(\\d+)").getMatch(0);
        final int season = (int) JavaScriptEngineFactory.toLong(entries.get("season"), -1);
        int episode = (int) JavaScriptEngineFactory.toLong(entries.get("episode"), -1);
        final boolean isDRM = ((Boolean) entries.get("isDrm")).booleanValue();
        if (episode == -1 && episode_str != null) {
            /* Fallback which should usually not be required */
            episode = (int) Long.parseLong(episode_str);
        }
        final String description = (String) entries.get("articleLong");
        /* Title or subtitle of a current series-episode */
        String title = (String) entries.get("title");
        if (title == null || formatTitle == null || date == null) {
            /* This should never happen */
            return AvailableStatus.UNCHECKABLE;
        }
        String filename_beginning = "";
        final AvailableStatus status;
        if (isDRM) {
            final TvnowConfigInterface cfg = PluginJsonConfig.get(jd.plugins.hoster.TvnowDe.TvnowConfigInterface.class);
            filename_beginning = "[DRM]";
            if (cfg.isEnableDRMOffline()) {
                /* Show as offline although it is online ... but we cannot download it anyways! */
                downloadLink.setAvailable(false);
                status = AvailableStatus.FALSE;
            } else {
                /* Show as online although we cannot download it */
                downloadLink.setAvailable(true);
                status = AvailableStatus.TRUE;
            }
        } else {
            /* Show as online as it is downloadable and online */
            downloadLink.setAvailable(true);
            status = AvailableStatus.TRUE;
        }
        data.setShow(formatTitle);
        if (isValidTvStation(tv_station)) {
            data.setChannel(tv_station);
        }
        data.setReleaseDate(getDateMilliseconds(date));
        if (season != -1 && episode != -1) {
            data.setSeasonNumber(season);
            data.setEpisodeNumber(episode);
            /* Episodenumber is in title --> Remove it as we insert it via 'S00E00' format so we do not need it twice! */
            if (title.matches("Folge \\d+")) {
                /* No usable title available - remove it completely! */
                title = null;
            } else if (title.matches("Folge \\d+: .+")) {
                /* Improve title by removing redundant episodenumber from it. */
                title = title.replaceAll("(Folge \\d+: )", "");
            }
        }
        if (!StringUtils.isEmpty(title)) {
            data.setTitle(title);
        }
        final String filename = filename_beginning + MediathekHelper.getMediathekFilename(downloadLink, data, false, false);
        try {
            if (FilePackage.isDefaultFilePackage(downloadLink.getFilePackage())) {
                final FilePackage fp = FilePackage.getInstance();
                fp.setName(formatTitle);
                fp.add(downloadLink);
            }
            if (!StringUtils.isEmpty(description) && downloadLink.getComment() == null) {
                downloadLink.setComment(description);
            }
        } catch (final Throwable e) {
        }
        downloadLink.setFinalFileName(filename);
        return status;
    }

    public static boolean isValidTvStation(final String tv_station) {
        return !StringUtils.isEmpty(tv_station) && !tv_station.equalsIgnoreCase("none") && !tv_station.equalsIgnoreCase("tvnow");
    }

    /* Last revision with old handling: BEFORE 38232 (30393) */
    private void download(final DownloadLink downloadLink) throws Exception {
        final boolean isFree = ((Boolean) entries.get("free")).booleanValue();
        final boolean isDRM = ((Boolean) entries.get("isDrm")).booleanValue();
        final String movieID = Long.toString(JavaScriptEngineFactory.toLong(entries.get("id"), -1));
        if (movieID.equals("-1")) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (isDRM) {
            /* There really is no way to download these videos and if, you will get encrypted trash data so let's just stop here. */
            throw new PluginException(LinkStatus.ERROR_FATAL, "Unsupported streaming type [DRM]");
        }
        final String urlpart = getURLPart(downloadLink);
        br.getPage(API_BASE + "/movies/" + urlpart + "?fields=manifest");
        entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaObject(br.toString());
        entries = (LinkedHashMap<String, Object>) entries.get("manifest");
        /* 2018-04-18: So far I haven't seen a single http stream! */
        // final String urlHTTP = (String) entries.get("hbbtv");
        final String hdsMaster = (String) entries.get("hds");
        String hlsMaster = (String) entries.get("hlsclear");
        if (StringUtils.isEmpty(hlsMaster)) {
            hlsMaster = (String) entries.get("hlsfairplay");
            /* 2018-05-04: Only "hls" == Always DRM */
            // if (StringUtils.isEmpty(hlsMaster)) {
            // hlsMaster = (String) entries.get("hls");
            // }
        }
        if (!StringUtils.isEmpty(hlsMaster)) {
            hlsMaster = hlsMaster.replaceAll("(filter=.*?)(&|$)", "");// show all available qualities
            br.getPage(hlsMaster);
            final HlsContainer hlsbest = HlsContainer.findBestVideoByBandwidth(HlsContainer.getHlsQualities(this.br));
            if (hlsbest == null) {
                /* No content available --> Probably DRM protected */
                throw new PluginException(LinkStatus.ERROR_FATAL, "Unsupported streaming type [DRM]");
            }
            checkFFmpeg(downloadLink, "Download a HLS Stream");
            try {
                dl = new HLSDownloader(downloadLink, br, hlsbest.getDownloadurl());
            } catch (final Throwable e) {
                /*
                 * 2017-11-15: They've changed these URLs to redirect to image content (a pixel). Most likely we have a broken HLS url -->
                 * Download not possible, only crypted HDS available.
                 */
                throw new PluginException(LinkStatus.ERROR_FATAL, "Unsupported streaming type [DRM]");
            }
            dl.startDownload();
        } else {
            /* hds */
            if (!isFree) {
                /*
                 * We found no downloadurls plus the video is not viewable for free --> Paid content. TODO: Maybe check if it is
                 * downloadable once a user bought it --> Probably not as chances are high that it will be DRM protected!
                 */
                throw new PluginException(LinkStatus.ERROR_FATAL, "Download nicht möglich (muss gekauft werden)");
            }
            throw new PluginException(LinkStatus.ERROR_FATAL, "HDS streaming is not (yet) supported");
            // /* Now we're sure that our .mp4 availablecheck-filename is correct */
            // downloadLink.setFinalFileName(downloadLink.getName());
            // /* TODO */
            // if (true) {
            // // throw new PluginException(LinkStatus.ERROR_FATAL, "Unsupported streaming type [HDS]");
            // throw new PluginException(LinkStatus.ERROR_FATAL, "Unsupported streaming type / encrypted HDS");
            // }
            // if (url_hds.matches(this.HDSTYPE_NEW_DETAILED)) {
            // throw new PluginException(LinkStatus.ERROR_FATAL, "Unsupported streaming type / encrypted HDS");
            // }
            // if (dllink.matches(this.HDSTYPE_NEW_MANIFEST)) {
            // logger.info("2nd attempt to get final hds url");
            // /* TODO */
            // if (true) {
            // throw new PluginException(LinkStatus.ERROR_FATAL, "Unsupported streaming type / encrypted HDS");
            // }
            // final XPath xPath = xmlParser(dllink);
            // final NodeList nl = (NodeList) xPath.evaluate("/manifest/media", doc, XPathConstants.NODESET);
            // final Node n = nl.item(0);
            // dllink = n.getAttributes().getNamedItem("href").getTextContent();
            // }
            // br.getPage(dllink);
            // final String hds = parseManifest();
            // dl = new HDSDownloader(downloadLink, br, url_hds);
            // dl.startDownload();
        }
    }

    private String getURLPart(final DownloadLink dl) throws PluginException, IOException {
        /* OLD rev: 39908 */
        // return new Regex(dl.getDownloadURL(), "/([a-z0-9\\-]+/[a-z0-9\\-]+)$").getMatch(0);
        /* 2018-12-12: New */
        final String regExPattern_Urlinfo = "https?://[^/]+/[^/]+/([^/]*?)/([^/]+/)?(.+)";
        Regex urlInfo = new Regex(dl.getPluginPatternMatcher(), regExPattern_Urlinfo);
        final String showname_url = urlInfo.getMatch(0);
        final String episodename_url = urlInfo.getMatch(2);
        /* 2018-12-27: TODO: Remove this old code - crawler will store all relevant information on DownloadLink via properties! */
        /* Find relevant information - first check if we've stored that before (e.g. URLs were added via decrypter) */
        String showname = dl.getStringProperty("url_showname", null);
        String episodename = dl.getStringProperty("url_episodetitle", null);
        boolean grabbed_url_info_via_website = false;
        if (StringUtils.isEmpty(showname) || StringUtils.isEmpty(episodename)) {
            /* No stored information available --> URLs have NOT been added via decrypter --> Now it might get a little bit complicated */
            final boolean showname_url_is_unsafe = showname_url == null || (new Regex(showname_url, "(\\-\\d+){1}$").matches() && !new Regex(showname_url, "(\\-\\d+){2}$").matches());
            if (!showname_url_is_unsafe) {
                /* URLs were not added via decrypter --> Try to use information in URL */
                logger.info("Using info from URL");
                showname = showname_url;
                episodename = episodename_url;
            } else if (StringUtils.isEmpty(showname) || StringUtils.isEmpty(episodename)) {
                final String url_old;
                if (dl.getPluginPatternMatcher().matches(TYPE_DEEPLINK)) {
                    /* 2018-12-20: Code unused at the moment */
                    /* TYPE_DEEPLINK --> old_url --> new_url */
                    logger.info("TYPE_DEEPLINK --> old_url");
                    br.getPage(dl.getPluginPatternMatcher());
                    if (br.getHttpConnection().getResponseCode() == 404) {
                        throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                    }
                    url_old = br.getRegex("webLink = \\'(https?://[^<>\"\\']+)\\'").getMatch(0);
                } else {
                    /* old_url --> new_url */
                    url_old = dl.getPluginPatternMatcher();
                }
                if (url_old == null) {
                    logger.warning("Failed to find old_url");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                logger.info("Expecting redirect from old linktype to new linktype");
                final boolean follow_redirects_setting_before = br.isFollowingRedirects();
                br.setFollowRedirects(false);
                br.getPage(url_old);
                /* Old linkformat should redirect to new linkformat */
                final String redirecturl = br.getRedirectLocation();
                /*
                 * We accessed the main-URL so it makes sense to at least check for a 404 at this stage to avoid requestion potentially dead
                 * URLÖs again via API!
                 */
                if (br.getHttpConnection().getResponseCode() == 404) {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                } else if (redirecturl == null) {
                    logger.warning("Redirect to new linktype failed");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                br.setFollowRedirects(follow_redirects_setting_before);
                logger.info("URL_old: " + dl.getPluginPatternMatcher() + " | URL_new: " + redirecturl);
                /* Cleanup for API requests if values haven't been set in crawler before */
                urlInfo = new Regex(redirecturl, regExPattern_Urlinfo);
                showname = urlInfo.getMatch(0);
                episodename = urlInfo.getMatch(2);
                if (StringUtils.isEmpty(showname) || StringUtils.isEmpty(episodename)) {
                    logger.warning("Failed to extract urlInfo from URL_new");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                grabbed_url_info_via_website = true;
            }
            if (StringUtils.isEmpty(showname) || StringUtils.isEmpty(episodename)) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            showname = cleanupShowTitle(showname);
            episodename = cleanupEpisodeTitle(episodename);
            if (grabbed_url_info_via_website) {
                /* Store information so we do not have to access that URL without API ever again. */
                storeUrlPartInfo(dl, showname, episodename, null, null, null);
                /* Store this info for future error cases */
                dl.setProperty("grabbed_url_info_via_website", true);
            }
        }
        final String urlpart = showname + "/" + episodename;
        return urlpart;
    }

    public static void storeUrlPartInfo(final DownloadLink dl, final String showname, final String episodename, final String thisStationName, final String formatID, final String episodeID) {
        dl.setProperty("url_showname", showname);
        dl.setProperty("url_episodetitle", episodename);
        if (thisStationName != null) {
            /* 2018-12-18: Not required to store at the moment but this might be relevant in the future */
            dl.setProperty("tv_station_name", thisStationName);
        }
        if (formatID != null && episodeID != null) {
            /* Even movies have a formatID and episodeID - both of these IDs are ALWAYS given! */
            dl.setProperty("id_format", formatID);
            dl.setProperty("id_episode", episodeID);
            /* Important: Make sure that crawler- and hosterplugin always set correct linkids! */
            dl.setLinkID(formatID + "/" + episodeID);
        }
    }

    /**
     * Removes parts of the show-title which are not allowed for API requests e.g. the show-ID. <br />
     * Keep ind mind that this may fail for Strings which end with numbers and do NOT contain a show-ID anymore e.g. BAD case: "koeln-1337".
     * Good case: "koeln-1337-506928"
     */
    public static String cleanupShowTitle(String showname) {
        if (showname == null) {
            return null;
        }
        showname = showname.replaceAll("\\-\\d+$", "");
        return showname;
    }

    /** Removes parts of the episode-title which are not allowed for API requests e.g. the show-ID. */
    public static String cleanupEpisodeTitle(String episodename) {
        if (episodename == null) {
            return null;
        }
        episodename = episodename.replaceAll("^episode\\-\\d+\\-", "");
        /* This part is tricky - we have to filter-out stuff which does not belong to their intern title ... */
        /* Examples: which shall NOT be modified: "super-8-kamera-von-1965", "folge-w-05" */
        if (!episodename.matches(".*?(folge|teil|w)\\-\\d+$") && !episodename.matches(".+\\d{4}\\-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}$") && !episodename.matches(".+\\-[12]\\d{3}")) {
            episodename = episodename.replaceAll("\\-\\d+$", "");
        }
        return episodename;
    }

    @Override
    public String getAGBLink() {
        return "http://rtl-now.rtl.de/nutzungsbedingungen";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        final TvnowConfigInterface cfg = PluginJsonConfig.get(jd.plugins.hoster.TvnowDe.TvnowConfigInterface.class);
        if (cfg.isEnableUnlimitedSimultaneousDownloads()) {
            return -1;
        } else {
            return 1;
        }
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        /* TODO: Fix this! */
        // final String ageCheck = br.getRegex("(Aus Jugendschutzgründen nur zwischen \\d+ und \\d+ Uhr abrufbar\\!)").getMatch(0);
        // if (ageCheck != null) {
        // throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, ageCheck, 10 * 60 * 60 * 1000l);
        // }
        download(downloadLink);
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
        if (link != null) {
            link.removeProperty(HDSDownloader.RESUME_FRAGMENT);
        }
    }

    @Override
    public void resetPluginGlobals() {
    }

    /** Formats the existing date to the 'general' date used for german TV online services: yyyy-MM-dd */
    public static long getDateMilliseconds(final String input) {
        if (input == null) {
            return -1;
        }
        return TimeFormatter.getMilliSeconds(input, "yyyy-MM-dd HH:mm:ss", Locale.GERMAN);
    }

    @Override
    public Class<? extends PluginConfigInterface> getConfigInterface() {
        return TvnowConfigInterface.class;
    }

    public static interface TvnowConfigInterface extends PluginConfigInterface {
        public static class TRANSLATION {
            public String getEnableUnlimitedSimultaneousDownloads_label() {
                /* Translation not required for this */
                return "Enable unlimited simultaneous downloads? [Warning this may cause issues]";
            }

            public String getEnableDRMOffline_label() {
                /* Translation not required for this */
                return "Display DRM protected content as offline (because it is not downloadable anyway)?";
            }
        }

        public static final TRANSLATION TRANSLATION = new TRANSLATION();

        @DefaultBooleanValue(false)
        @Order(10)
        boolean isEnableUnlimitedSimultaneousDownloads();

        void setEnableUnlimitedSimultaneousDownloads(boolean b);

        @DefaultBooleanValue(false)
        @Order(10)
        boolean isEnableDRMOffline();

        void setEnableDRMOffline(boolean b);
    }
}