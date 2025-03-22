package com.hypergryph.arknights.announce;

import com.hypergryph.arknights.core.file.IOTools;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/announce"})
public class Android {
    public Android() {
    }

    @RequestMapping({"/Android/preannouncement/280_1618473718.html"})
    public String PreAnnouncement() {
        return IOTools.ReadNormalFile(System.getProperty("user.dir") + "/data/static/announcement/280_1618473718.html");
    }

    @RequestMapping({"/Android/css/preannouncement.v_0_1_2.css"})
    public String PreAnnouncementCss() {
        return IOTools.ReadNormalFile(System.getProperty("user.dir") + "/data/static/css/preannouncement.v_0_1_2.css");
    }
}
