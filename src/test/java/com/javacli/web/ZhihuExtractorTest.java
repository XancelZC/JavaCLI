package com.javacli.web;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ZhihuExtractorTest {

    private final HtmlExtractor htmlExtractor = new HtmlExtractor();
    private final ZhihuExtractor zhihuExtractor = new ZhihuExtractor(htmlExtractor);

    @Test
    void detectsZhihuUrlsAndHtml() {
        assertTrue(ZhihuExtractor.isZhihu("https://www.zhihu.com/question/275359100", ""));
        assertTrue(ZhihuExtractor.isZhihu("https://zhuanlan.zhihu.com/p/676550756", ""));
        assertTrue(ZhihuExtractor.isZhihu("https://example.com", "<script id=\"js-initialData\"></script>"));
        assertFalse(ZhihuExtractor.isZhihu("https://example.com/blog", "<html><body>hello</body></html>"));
    }

    @Test
    void detectsAntiBotChallenge() {
        String challengeHtml = """
                <!DOCTYPE html><html><head>
                <meta id="zh-zse-ck" content="xyz123">
                </head><body>
                <script src="https://static.zhihu.com/zse-ck/v4/app.js"></script>
                </body></html>
                """;
        assertTrue(ZhihuExtractor.isChallengeOrLoginWall(challengeHtml));

        String captchaHtml = "<html><head><title>安全验证 - 知乎</title></head><body>请完成验证</body></html>";
        assertTrue(ZhihuExtractor.isChallengeOrLoginWall(captchaHtml));

        String normalHtml = "<html><head><title>正常页面</title></head><body>正常内容</body></html>";
        assertFalse(ZhihuExtractor.isChallengeOrLoginWall(normalHtml));
    }

    @Test
    void extractsQuestionAndAnswersFromInitialData() {
        String json = """
                {
                  "initialState": {
                    "entities": {
                      "questions": {
                        "275359100": {
                          "id": 275359100,
                          "title": "如何评价 Java 21 的虚拟线程特性？",
                          "detail": "<p>Java 21 带来了革命性的 Project Loom 虚拟线程。</p>",
                          "created": 1695000000
                        }
                      },
                      "answers": {
                        "1001": {
                          "id": 1001,
                          "author": { "name": "并发老司机" },
                          "voteupCount": 1500,
                          "content": "<p>虚拟线程彻底改变了高并发编程模型。</p><ul><li>轻量高效</li><li>调度简单</li></ul>",
                          "createdTime": 1695010000
                        },
                        "1002": {
                          "id": 1002,
                          "author": { "name": "初学者小白" },
                          "voteupCount": 35,
                          "content": "<p>学起来感觉非常直观！</p>",
                          "createdTime": 1695020000
                        }
                      }
                    }
                  }
                }
                """;

        String html = "<html><head><title>知乎测试</title><script id=\"js-initialData\" type=\"text/json\">"
                + json + "</script></head><body></body></html>";

        Document doc = Jsoup.parse(html, "https://www.zhihu.com/question/275359100");
        HtmlExtractor.Extracted extracted = zhihuExtractor.extract(doc, "https://www.zhihu.com/question/275359100");

        assertNotNull(extracted);
        assertEquals("如何评价 Java 21 的虚拟线程特性？", extracted.title());
        String md = extracted.markdown();
        assertTrue(md.contains("# 如何评价 Java 21 的虚拟线程特性？"));
        assertTrue(md.contains("Project Loom 虚拟线程"));
        assertTrue(md.contains("并发老司机"));
        assertTrue(md.contains("1500 赞同"));
        assertTrue(md.contains("- 轻量高效"));
        assertTrue(md.contains("初学者小白"));

        // 高赞回答应排在低赞回答前面
        int rankHigh = md.indexOf("并发老司机");
        int rankLow = md.indexOf("初学者小白");
        assertTrue(rankHigh < rankLow, "高赞回答应排在前面");
    }

    @Test
    void extractsArticleFromInitialData() {
        String json = """
                {
                  "initialState": {
                    "entities": {
                      "articles": {
                        "676550756": {
                          "id": 676550756,
                          "title": "深入理解现代垃圾回收器 ZGC",
                          "author": { "name": "JVM专家" },
                          "voteupCount": 888,
                          "created": 1695030000,
                          "content": "<p>ZGC 是低延迟垃圾回收器，停顿时间小于 1ms。</p><blockquote>核心技术是染色指针与读屏障。</blockquote>"
                        }
                      }
                    }
                  }
                }
                """;

        String html = "<html><head><script id=\"js-initialData\" type=\"text/json\">"
                + json + "</script></head><body></body></html>";

        Document doc = Jsoup.parse(html, "https://zhuanlan.zhihu.com/p/676550756");
        HtmlExtractor.Extracted extracted = zhihuExtractor.extract(doc, "https://zhuanlan.zhihu.com/p/676550756");

        assertNotNull(extracted);
        assertEquals("深入理解现代垃圾回收器 ZGC", extracted.title());
        String md = extracted.markdown();
        assertTrue(md.contains("# 深入理解现代垃圾回收器 ZGC"));
        assertTrue(md.contains("JVM专家"));
        assertTrue(md.contains("888"));
        assertTrue(md.contains("停顿时间小于 1ms"));
        assertTrue(md.contains("> 核心技术是染色指针"));
    }

    @Test
    void extractsFromZhihuDomFallback() {
        String html = """
                <html><head><title>DOM测试</title></head>
                <body>
                  <h1 class="QuestionHeader-title">DOM方式解析的问题</h1>
                  <div class="AnswerItem">
                    <div class="AuthorInfo-name">DOM回答者</div>
                    <div class="RichContent-inner">
                      <p>这是从 DOM 里直接抽取的回答内容。</p>
                    </div>
                  </div>
                </body></html>
                """;

        Document doc = Jsoup.parse(html, "https://www.zhihu.com/question/123");
        HtmlExtractor.Extracted extracted = zhihuExtractor.extract(doc, "https://www.zhihu.com/question/123");

        assertNotNull(extracted);
        assertEquals("DOM方式解析的问题", extracted.title());
        assertTrue(extracted.markdown().contains("DOM回答者"));
        assertTrue(extracted.markdown().contains("这是从 DOM 里直接抽取的回答内容"));
    }

    @Test
    void htmlExtractorDelegatesToZhihuExtractor() {
        String json = """
                {
                  "initialState": {
                    "entities": {
                      "articles": {
                        "123": {
                          "title": "HtmlExtractor集成测试",
                          "author": { "name": "作者A" },
                          "voteupCount": 10,
                          "content": "<p>测试内容自动化抽取</p>"
                        }
                      }
                    }
                  }
                }
                """;
        String html = "<html><head><script id=\"js-initialData\">" + json + "</script></head><body></body></html>";
        HtmlExtractor.Extracted out = htmlExtractor.extract(html, "https://zhuanlan.zhihu.com/p/123");
        assertEquals("HtmlExtractor集成测试", out.title());
        assertTrue(out.markdown().contains("测试内容自动化抽取"));
    }

    @Test
    void htmlExtractorReportsAntiBotChallengeForZhihu() {
        String challengeHtml = """
                <html><head><meta id="zh-zse-ck" content="challenge_data"></head>
                <body>知乎，让每一次点击都充满意义</body></html>
                """;
        HtmlExtractor.Extracted out = htmlExtractor.extract(challengeHtml, "https://www.zhihu.com/question/123");
        assertTrue(out.markdown().contains("触发了知乎反爬风控或强登录拦截"));
    }
}
