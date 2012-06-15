package org.neo4j.jdbc.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.neo4j.jdbc.*;
import org.restlet.Client;
import org.restlet.representation.Representation;
import org.restlet.resource.ClientResource;
import org.restlet.resource.ResourceException;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.util.Map;

/**
* @author mh
* @since 15.06.12
*/
public class RestQueryExecutor implements QueryExecutor {
    protected final static Log log = LogFactory.getLog(RestQueryExecutor.class);
    private final static Client client = new Client("HTTP");

    private String url;
        private ClientResource cypherResource;
        private ObjectMapper mapper = new ObjectMapper();
        private Version version;

    public RestQueryExecutor(String connectionUrl, String user, String password) throws SQLException {
        try
                {
                    url = "http" + connectionUrl;
                    if (log.isInfoEnabled())log.info("Connecting to URL "+url);
                    Resources resources = new Resources(url, client);

                    if (user!=null && password!=null) {
                        resources.setAuth(user, password);
                    }

                    Resources.DiscoveryClientResource discovery = resources.getDiscoveryResource();

                    version = new Version(discovery.getVersion());

                    String cypherPath = discovery.getCypherPath();

                    cypherResource = resources.getCypherResource(cypherPath);
                } catch (IOException e)
                {
                    throw new SQLNonTransientConnectionException(e);
                }
    }

    public ExecutionResult doExecuteQuery(String query, Map<String, Object> parameters) throws Exception {
    try
    {
        ObjectNode queryNode = queryParameter(query, parameters);

        final ClientResource resource = new ClientResource(cypherResource);
        Representation rep = resource.post(queryNode.toString());

        JsonNode node = mapper.readTree(rep.getReader());
        final ResultParser parser = new ResultParser(node);
        return new ExecutionResult(parser.getColumns(), parser.streamData());

    } catch (ResourceException e)
    {
        throw new SQLException(e.getStatus().getReasonPhrase(),e);
    }
}

    @Override
    public void stop() throws Exception {
        ((Client) cypherResource.getNext()).stop();
    }

    @Override
    public Version getVersion() {
        return version;
    }

    private ObjectNode queryParameter(String query, Map<String, Object> parameters) {
            ObjectNode queryNode = mapper.createObjectNode();
            queryNode.put("query", escapeQuery(query));
            queryNode.put("params", parametersNode(parameters));
            return queryNode;
        }

        private String escapeQuery(String query) {
            query = query.replace('\"', '\'');
            query = query.replace('\n', ' ');
            return query;
        }

        private ObjectNode parametersNode(Map<String, Object> parameters) {
            ObjectNode params = mapper.createObjectNode();
            for (Map.Entry<String, Object> entry : parameters.entrySet())
            {
                final String name = entry.getKey();
                final Object value = entry.getValue();
                if (value==null) {
                    params.putNull(name);
                } else if (value instanceof String)
                    params.put(name, value.toString());
                else if (value instanceof Integer)
                    params.put(name, (Integer) value);
                else if (value instanceof Long)
                    params.put(name, (Long) value);
                else if (value instanceof Boolean)
                    params.put(name, (Boolean) value);
                else if (value instanceof BigDecimal)
                    params.put(name, (BigDecimal) value);
                else if (value instanceof Double)
                    params.put(name, (Double) value);
                else if (value instanceof byte[])
                    params.put(name, (byte[]) value);
                else if (value instanceof Float)
                    params.put(name, (Float) value);
                else if (value instanceof Number) {
                    final Number number = (Number) value;
                    if (number.longValue()==number.doubleValue()) {
                        params.put(name, number.longValue());
                    } else {
                        params.put(name, number.doubleValue());
                    }
                }
            }
            return params;
        }
}
