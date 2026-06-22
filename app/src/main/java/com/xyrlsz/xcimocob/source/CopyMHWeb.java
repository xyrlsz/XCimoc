package com.xyrlsz.xcimocob.source;

import com.google.common.collect.Lists;
import com.xyrlsz.xcimocob.model.Chapter;
import com.xyrlsz.xcimocob.model.Comic;
import com.xyrlsz.xcimocob.model.ImageUrl;
import com.xyrlsz.xcimocob.model.Source;
import com.xyrlsz.xcimocob.soup.Node;
import com.xyrlsz.xcimocob.utils.IdCreator;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import okhttp3.Headers;
import okhttp3.Request;

public class CopyMHWeb extends CopyMHBase {
    public static final int TYPE = 27;
    public static final String DEFAULT_TITLE = "拷贝漫画Web";

    public CopyMHWeb(Source source) {
        super(source, new Category(), TYPE, true, true);
    }

    public static Source getDefaultSource() {
        return new Source(null, DEFAULT_TITLE, TYPE, true, website);
    }


    @Override
    public String getUA() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36 Edg/146.0.0.0";
    }


    @Override
    public List<Chapter> parseChapter(String html, Comic comic, Long sourceComic)
            throws JSONException {

        List<Chapter> list = new LinkedList<>();
        Node body = new Node(html);
        List<Node> tabGroup = body.list(".table-default-box");
        List<Node> tabGroupName = body.list(".upLoop > span");
        for (int i = 0; i < tabGroup.size(); i++) {
            Node tabNode = tabGroup.get(i);
            Node allTab = tabNode.list(".tab-content > .tab-pane").get(0);
            List<Node> chapterNodes = allTab.list("ul > a");
            chapterNodes = Lists.reverse(chapterNodes);
            for (Node node : chapterNodes) {
                String title = node.attr("title");
                if (title == null || title.isEmpty()) {
                    title = node.text("li").trim();
                }
                String path = node.href();
                if (!title.isEmpty()) {
                    list.add(new Chapter(null, sourceComic, title, path, tabGroupName.get(i).text()));
                }
            }
        }

        for (int j = 0; j < list.size(); j++) {
            long id = IdCreator.createChapterId(sourceComic, j);
            list.get(j).setId(id);
        }
        return list;
    }


    @Override
    public Request getImagesRequest(String cid, String path) {
        String url = website + path;
        return new Request.Builder()
                .headers(getHeader())
                .url(url)
                .build();
    }

    @Override
    public List<ImageUrl> parseImages(String html, Chapter chapter) throws JSONException {
        List<ImageUrl> list = new ArrayList<>();
        Node body = new Node(html);
        List<Node> imageNodes = body.list("ul.comicContent-list > li");
        for (int i = 1; i <= imageNodes.size(); i++) {
            long comicChapter = chapter.getId();
            long id = IdCreator.createImageId(comicChapter, i);
            String imgUrl = imageNodes.get(i - 1).list("img").get(0).attr("data-src");
            imgUrl = imgUrl.replaceAll("c\\d+x\\.[a-zA-Z]+$", "c" + 1500 + "x.webp");
            list.add(new ImageUrl(id, comicChapter, i, imgUrl, false));
        }
        return list;
    }

    @Override
    public Headers getHeader() {
        return Headers.of("user-agent", getUA());
    }


}
