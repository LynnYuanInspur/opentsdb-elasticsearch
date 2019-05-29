package net.opentsdb.tsd;

import com.google.common.collect.Maps;
import com.stumbleupon.async.Deferred;
import net.opentsdb.core.TSDB;
import net.opentsdb.search.ElasticSearch;
import net.opentsdb.stats.StatsCollector;
import net.opentsdb.tools.BuildData;
import net.opentsdb.utils.JSON;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.util.EntityUtils;
import org.hbase.async.Counter;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CancellationException;

/**
 * @author lynn
 * @ClassName net.opentsdb.tsd.SearchHttpRpcPlugin
 * @Description TODO
 * @Date 19-5-29 上午8:36
 * @Version 1.0
 **/
public class SearchHttpRpcPlugin extends HttpRpcPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(SearchHttpRpcPlugin.class);

    private TSDB tsdb;

    private ElasticSearch es;

    /** The index of document used. */
    protected String index;

    private CloseableHttpAsyncClient http_client;

    protected final Counter search_errors = new Counter();
    protected final Counter search_success = new Counter();
    protected final Counter search_invalid = new Counter();

    @Override
    public void initialize(TSDB tsdb) {
        this.tsdb = tsdb;
        es = (ElasticSearch) tsdb.getSearchPlugin();
        LOG.info("Initialized SearchHttpRpcPlugin");
    }

    @Override
    public Deferred<Object> shutdown() {
        return Deferred.fromResult(null);
    }

    @Override
    public String version() {
        return BuildData.version;
    }

    @Override
    public void collectStats(StatsCollector collector) {
        collector.record("http.rpcplugin.search.errors", search_errors);
        collector.record("http.rpcplugin.search.success", search_success);
        collector.record("http.rpcplugin.search.invalid_requests", search_invalid);
    }

    @Override
    public String getPath() {
        return "search";
    }

    @Override
    public void execute(TSDB tsdb, HttpRpcPluginQuery query) throws IOException {
        // only accept GET/POST for now
        if (query.request().getMethod() != HttpMethod.GET &&
                query.request().getMethod() != HttpMethod.POST) {
            throw new BadRequestException(HttpResponseStatus.METHOD_NOT_ALLOWED,
                    "Method not allowed", "The HTTP method [" + query.method().getName() +
                    "] is not permitted for this endpoint");
        }

        final String[] uri = query.explodePath();
        final String endpoint = uri.length > 1 ? uri[2].toLowerCase() : "";

        if ("version".equals(endpoint)) {
            handleVersion(query);
        } else if ("index".equals(endpoint)) {
            handleSearch(query);
        } else {
            search_invalid.increment();
            throw new BadRequestException(HttpResponseStatus.NOT_IMPLEMENTED,
                    "Hello. You have reached an API that has been disconnected. "
                            + "Please call again.");
        }

    }

    /**
     * Prints Version information for the various components.
     * @param query A non-null HTTP query to parse and respond to.
     */
    private void handleVersion(final HttpRpcPluginQuery query) {
        final Map<String, Map<String, String>> versions = Maps.newHashMap();

        Map<String, String> version = Maps.newHashMap();
        version.put("version", BuildData.version);
        version.put("short_revision", BuildData.short_revision);
        version.put("full_revision", BuildData.full_revision);
        version.put("timestamp", Long.toString(BuildData.timestamp));
        version.put("repo_status", BuildData.repo_status.toString());
        version.put("user", BuildData.user);
        version.put("host", BuildData.host);
        version.put("repo", BuildData.repo);
        versions.put("tsdb", version);

        search_success.increment();
        // TODO - plugin version
        query.sendBuffer(HttpResponseStatus.OK,
                ChannelBuffers.wrappedBuffer(JSON.serializeToBytes(versions)),
                "application/json");

    }

    /**
     * Prints Version information for the various components.
     * @param query A non-null HTTP query to parse and respond to.
     */
    private void handleSearch(final HttpRpcPluginQuery query) {
        if (query == null || query.getContent() == null
            || "".equals(query.getContent())) {
            search_invalid.increment();
            Map<String, String> error = Maps.newHashMap();
            error.put("error", "query content must be not null!");
            query.sendBuffer(HttpResponseStatus.EXPECTATION_FAILED,
                    ChannelBuffers.wrappedBuffer(JSON.serializeToBytes(error)),
                    "application/json");
            return;
        }

        final Deferred<Object> result = new Deferred<Object>();

        final class AsyncCB implements FutureCallback<HttpResponse> {
            @Override
            public void cancelled() {
                result.callback(new CancellationException("Index call was cancelled."));
                search_errors.increment();
            }

            @Override
            public void completed(final HttpResponse content) {
                result.callback(content);
                search_success.increment();
            }

            @Override
            public void failed(final Exception e) {
                result.callback(e);
                search_errors.increment();
            }

        }

        final StringBuilder uri = new StringBuilder(es.host())
                .append("/")
                .append(es.index())
                .append("/_search");
        final HttpPost post = new HttpPost(uri.toString());
        post.setHeader("Content-Type", "application/json;charset=utf8");
        post.setEntity(new StringEntity(query.getContent(), "UTF-8"));
        if(LOG.isDebugEnabled()){
            LOG.debug("send request\n{} \nto uri {}", query.getContent(), uri);
        }
        if (es.asyncReplication()) {
            uri.append("?replication=async");
        }

        es.httpClient().execute(post, new AsyncCB());

        try {
            Object object = result.join(1000);
            if(object instanceof HttpResponse){
                HttpResponse content = (HttpResponse) object;
                try {
                    query.sendBuffer(HttpResponseStatus.valueOf(content.getStatusLine().getStatusCode()),
                            ChannelBuffers.wrappedBuffer(EntityUtils.toByteArray(content.getEntity())),
                            "application/json");
                } catch (Exception e) {
                    LOG.error("Unexpected exception parsing content", e);
                    result.callback(e);
                } finally {
                    try {
                        EntityUtils.consume(content.getEntity());
                    } catch (IOException e) { }
                }
            }else if(object instanceof Exception){
                Map<String, String> exception = Maps.newHashMap();
                exception.put("exception", ((Exception) object).getMessage());
                query.sendBuffer(HttpResponseStatus.BAD_REQUEST,
                        ChannelBuffers.wrappedBuffer(JSON.serializeToBytes(exception)),
                        "application/json");
            }
        }catch (Exception e){
            Map<String, String> exception = Maps.newHashMap();
            exception.put("exception",  e.getMessage());
            query.sendBuffer(HttpResponseStatus.BAD_REQUEST,
                    ChannelBuffers.wrappedBuffer(JSON.serializeToBytes(exception)),
                    "application/json");
        }
    }
}
