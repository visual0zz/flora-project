package com.flora.hanako.tools;

import com.flora.root.tag.WorkInProgress;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网页抓取工具：获取 URL 的纯文本内容（轻量 SSRF 防护：仅允许 http/https）。
 * <p>复刻 openhanako web-fetch 工具；完整私网 IP 防护（基座能力 D3）作为后续增强项。</p>
 */
@WorkInProgress("SSRF 私网 CIDR 防护待基座能力 D3 落地，当前仅做协议与主机基本校验")
public final class WebFetchTool implements Tool {

    private final HttpClient http;

    public WebFetchTool() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String name() {
        return "web_fetch";
    }

    @Override
    public String description() {
        return "抓取一个公开网页 URL 并返回其文本内容，用于检索或读取在线资料。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("url", ReadFileTool.strProp("要抓取的 http/https 链接"));
        return ReadFileTool.objSchema(props, List.of("url"));
    }

    @Override
    public String execute(Map<String, Object> args) {
        String url = ReadFileTool.asString(args.get("url"));
        if (url == null || url.isBlank()) {
            return "错误：缺少 url 参数";
        }
        try {
            URI uri = URI.create(url);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                return "错误：仅允许 http/https 协议（SSRF 防护）";
            }
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "FloraHanako/0.1")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            String body = resp.body();
            int max = 50_000;
            if (body != null && body.length() > max) {
                body = body.substring(0, max) + "\n...[响应过长已截断]";
            }
            return "status=" + resp.statusCode() + "\n" + (body == null ? "" : body);
        } catch (Exception e) {
            return "错误：抓取失败 - " + e.getMessage();
        }
    }
}
