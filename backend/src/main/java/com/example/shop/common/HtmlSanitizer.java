package com.example.shop.common;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 富文本描述的白名单清洗（技术实现项，见需求 A.1-5「组内拟定」）。
 *
 * 不引入第三方依赖，用正则实现：
 * 1. 删掉注释与 script/style/iframe/object/embed/form 等整块危险标签（含内容）；
 * 2. 标签只保留白名单内的，其余标签被丢弃但保留其中文本；
 * 3. 属性只保留白名单内的，on* 事件属性一律丢弃；
 * 4. src/href 只允许 http(s)、相对路径与 data:image/video，其他协议（javascript: 等）丢弃。
 *
 * 说明：这是面向课程项目的轻量实现；生产环境应改用 jsoup 的 Safelist。
 */
public final class HtmlSanitizer {

    private HtmlSanitizer() {
    }

    /** 允许保留的标签：覆盖图文混排所需的文本、图片、视频、列表、表格。 */
    private static final Set<String> ALLOWED_TAGS = Set.of(
            "p", "br", "hr", "div", "span",
            "strong", "b", "em", "i", "u", "s", "del", "ins", "sub", "sup", "mark",
            "h1", "h2", "h3", "h4", "h5", "h6",
            "ul", "ol", "li", "blockquote", "pre", "code",
            "a", "img", "video", "source", "audio", "figure", "figcaption",
            "table", "thead", "tbody", "tr", "td", "th", "caption");

    /** 允许保留的属性。 */
    private static final Set<String> ALLOWED_ATTRS = Set.of(
            "src", "href", "alt", "title", "width", "height",
            "controls", "poster", "target", "rel", "type", "colspan", "rowspan", "start");

    private static final Set<String> DANGEROUS_TAGS = Set.of(
            "script", "style", "iframe", "object", "embed", "form", "input", "textarea",
            "button", "select", "option", "meta", "link", "base", "svg", "math", "applet");

    /** 危险标签整块（含内部内容）。 */
    private static final Pattern DANGEROUS_BLOCK = Pattern.compile(
            "(?is)<(" + String.join("|", DANGEROUS_TAGS) + ")\\b[^>]*>.*?</\\1\\s*>");

    /** 没有闭合体的危险标签（自闭合或未闭合）。 */
    private static final Pattern DANGEROUS_SINGLE = Pattern.compile(
            "(?is)<(" + String.join("|", DANGEROUS_TAGS) + ")\\b[^>]*/?>");

    private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern ENDS_WITH_COMMENT = Pattern.compile("(?s)<!--.*$");
    private static final Pattern TAG = Pattern.compile("(?s)<\\s*(/?)\\s*([a-zA-Z][a-zA-Z0-9]*)((?:[^>\"']|\"[^\"]*\"|'[^']*')*)>");
    private static final Pattern ATTR = Pattern.compile(
            "([a-zA-Z_:][-a-zA-Z0-9_:.]*)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+)))?");

    private static final Pattern SAFE_URI = Pattern.compile(
            "(?i)^(https?://|/|#|\\./|\\.\\./|data:(image|video|audio)/)");

    /**
     * 清洗富文本。null 或空白返回 null（描述可为空）。
     */
    public static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String s = COMMENT.matcher(html).replaceAll("");
        s = ENDS_WITH_COMMENT.matcher(s).replaceAll("");
        s = DANGEROUS_BLOCK.matcher(s).replaceAll("");
        s = DANGEROUS_SINGLE.matcher(s).replaceAll("");

        Matcher m = TAG.matcher(s);
        StringBuilder out = new StringBuilder(s.length());
        int last = 0;
        while (m.find()) {
            out.append(s, last, m.start());
            last = m.end();
            boolean closing = !m.group(1).isEmpty();
            String tag = m.group(2).toLowerCase();
            if (!ALLOWED_TAGS.contains(tag)) {
                // 标签丢弃，内容保留
                continue;
            }
            if (closing) {
                out.append("</").append(tag).append('>');
                continue;
            }
            String attrs = filterAttributes(m.group(3));
            out.append('<').append(tag).append(attrs).append('>');
        }
        out.append(s, last, s.length());
        return out.toString();
    }

    private static String filterAttributes(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Matcher m = ATTR.matcher(raw);
        while (m.find()) {
            String name = m.group(1).toLowerCase();
            if (!ALLOWED_ATTRS.contains(name)) {
                continue;
            }
            String value = m.group(2) != null ? m.group(2)
                    : m.group(3) != null ? m.group(3)
                    : m.group(4);
            if (value == null) {
                // 布尔属性（如 controls）
                sb.append(' ').append(name);
                continue;
            }
            if (("src".equals(name) || "href".equals(name) || "poster".equals(name))
                    && !SAFE_URI.matcher(value).find()) {
                continue;
            }
            sb.append(' ').append(name).append("=\"").append(escapeAttr(value)).append('"');
        }
        return sb.toString();
    }

    private static String escapeAttr(String v) {
        return v.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
