package de.baumann.browser.objects;

public class CustomRedirect {
    public String source;
    public String target;

    public CustomRedirect(String source, String target) {
        this.source = source;
        this.target = target;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSource() {
        return source;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getTarget() {
        return target;
    }
}
