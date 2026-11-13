package com.example.travelplatform.sharing;

import com.example.travelplatform.guide.service.GuideService;
import com.example.travelplatform.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.transaction.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
public class ShareController {
    private final GuideService guides;
    private final UserService users;
    private final PublicContentMapper mapper;
    private final String origin;
    public ShareController(GuideService guides,UserService users,PublicContentMapper mapper,
                           @Value("${travel.site.public-base-url:http://localhost:5173}") String rawOrigin) {
        this.guides=guides;this.users=users;this.mapper=mapper;
        URI uri=URI.create(rawOrigin);
        if(!("https".equals(uri.getScheme())||"http".equals(uri.getScheme()))||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!(uri.getPath().isEmpty()||uri.getPath().equals("/")))
            throw new IllegalArgumentException("Public site base URL must be an HTTP origin");
        this.origin=rawOrigin.replaceAll("/+$","");
    }
    @GetMapping(value="/share/guides/{id}",produces=MediaType.TEXT_HTML_VALUE)
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ResponseEntity<String> guide(@PathVariable String id) {
        var guide=guides.publicDetail(id);
        users.publicUserByUsername(guide.author().username());
        String description=guide.summary()==null?"阅读"+guide.author().nickname()+"的旅行手记，选择关联景点规划自己的旅程。":guide.summary();
        return html(page(guide.title(),description,"/share/guides/"+guide.id(),"/guides/"+guide.id(),guide.coverImageUrl(),"article"));
    }
    @GetMapping(value="/share/users/{username}",produces=MediaType.TEXT_HTML_VALUE)
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ResponseEntity<String> creator(@PathVariable String username) {
        var user=users.publicUserByUsername(username);
        String encoded=encode(user.getUsername());
        return html(page(user.getNickname()+"的旅行手记",user.getBio()==null?"在山水间阅读这位创作者的公开旅行手记。":user.getBio(),"/share/users/"+encoded,"/users/"+encoded,user.getAvatarUrl(),"profile"));
    }
    @GetMapping(value="/robots.txt",produces=MediaType.TEXT_PLAIN_VALUE)
    public String robots() {
        return "User-agent: *\nAllow: /share/\nDisallow: /api/\nDisallow: /me/\nDisallow: /admin/\nDisallow: /itineraries/\nDisallow: /login\nDisallow: /register\nSitemap: "+origin+"/sitemap.xml\n";
    }
    @GetMapping(value="/sitemap.xml",produces=MediaType.APPLICATION_XML_VALUE)
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public String sitemap() {
        StringBuilder xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
        for(long id:mapper.guideIds())xml.append("<url><loc>").append(escape(origin+"/share/guides/"+id)).append("</loc></url>");
        for(String username:mapper.usernames())xml.append("<url><loc>").append(escape(origin+"/share/users/"+encode(username))).append("</loc></url>");
        return xml.append("</urlset>").toString();
    }
    private String page(String title,String description,String canonical,String target,String image,String type) {
        String fullTitle=escape(title+" · 山水间"),summary=escape(description),url=escape(origin+canonical);
        String imageMeta=image!=null&&image.startsWith("/uploads/")?"<meta property=\"og:image\" content=\""+escape(origin+image)+"\">":"";
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            +"<title>"+fullTitle+"</title><meta name=\"description\" content=\""+summary+"\"><link rel=\"canonical\" href=\""+url+"\">"
            +"<meta property=\"og:type\" content=\""+type+"\"><meta property=\"og:site_name\" content=\"山水间\"><meta property=\"og:locale\" content=\"zh_CN\">"
            +"<meta property=\"og:title\" content=\""+fullTitle+"\"><meta property=\"og:description\" content=\""+summary+"\"><meta property=\"og:url\" content=\""+url+"\">"+imageMeta
            +"<meta name=\"twitter:card\" content=\"summary\"><meta name=\"twitter:title\" content=\""+fullTitle+"\"><meta name=\"twitter:description\" content=\""+summary+"\">"
            +"</head><body><main><h1>"+escape(title)+"</h1><p>"+summary+"</p><p><a href=\""+escape(origin+target)+"\">在山水间打开页面</a></p></main></body></html>";
    }
    private static ResponseEntity<String> html(String value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","default-src 'none'; base-uri 'none'; frame-ancestors 'none'").body(value);
    }
    private static String escape(String value) {return HtmlUtils.htmlEscape(value,StandardCharsets.UTF_8.name());}
    private static String encode(String value) {return URLEncoder.encode(value,StandardCharsets.UTF_8);}
}
