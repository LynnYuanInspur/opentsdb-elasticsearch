package net.opentsdb.search.schemas;

/**
 * @author lynn
 * @ClassName net.opentsdb.search.schemas.MetaDoc
 * @Description TODO
 * @Date 19-5-28 下午2:09
 * @Version 1.0
 **/
public class MetaDoc<M> {

    private String type;

    private M meta;

    public String getType() {
        return type;
    }

    public M getMeta() {
        return meta;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setMeta(M meta) {
        this.meta = meta;
    }

    public MetaDoc withType(String type) {
        this.setType(type);
        return this;
    }

    public MetaDoc withMeta(M meta) {
        this.setMeta(meta);
        return this;
    }
}
