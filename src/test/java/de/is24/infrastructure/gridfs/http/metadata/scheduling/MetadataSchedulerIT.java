package de.is24.infrastructure.gridfs.http.metadata.scheduling;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.WriteConcern;
import de.is24.infrastructure.gridfs.http.web.boot.AbstractContainerAndMongoDBStarter;
import de.is24.infrastructure.gridfs.http.web.boot.LocalOnly;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.UnknownHostException;

import static com.jayway.awaitility.Awaitility.await;
import static com.jayway.awaitility.Duration.TEN_SECONDS;
import static de.is24.infrastructure.gridfs.http.domain.RepoType.SCHEDULED;
import static de.is24.infrastructure.gridfs.http.mongo.DatabaseStructure.REPO_ENTRY_COLLECTION;
import static de.is24.infrastructure.gridfs.http.utils.RepositoryUtils.uniqueRepoName;
import static de.is24.infrastructure.gridfs.http.utils.RpmUtils.RPM_FILE;
import static de.is24.infrastructure.gridfs.http.web.RepoTestUtils.uploadRpm;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.apache.http.util.EntityUtils.consume;

@LocalOnly
public class MetadataSchedulerIT extends AbstractContainerAndMongoDBStarter {
  private String repoUrl;
  private String reponame;

  @Before
  public void setup() throws Exception {
    reponame = uniqueRepoName();
    repoUrl = deploymentURL + "/repo/" + reponame;
    uploadRpm(repoUrl, RPM_FILE.getPath());
  }

  @After
  public void tearDown() throws Exception {
    getRepoEntryCollection().remove(mongoRepoEntry(), WriteConcern.FSYNC_SAFE);
  }

  @Test
  public void metaDataIsCreatedForScheduledRepositories() throws Exception {
    assertGetRepoMdXmlReturns(SC_NOT_FOUND);
    insertScheduledRepoEntryIntoMongoDb();
    assertGetRepoMdXmlReturns(SC_OK);
  }

  @Test
  public void metaDataIsCreatedAtRegularInterval() throws Exception {
    insertScheduledRepoEntryIntoMongoDb();
    assertGetRepoMdXmlReturns(SC_OK);

    final String someRevision = getRepoMdXmlRevision();
    uploadRpm(repoUrl, "src/test/resources/rpms/valid.headertoyumpackage.noarch.rpm");

    await("repoMdXml revision changes").atMost(TEN_SECONDS).until(() -> {
      String laterRevision = getRepoMdXmlRevision();
      return !someRevision.equals(laterRevision);
    });
  }

  private String getRepoMdXmlRevision() throws ParserConfigurationException, SAXException, IOException {
    DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    Document document = builder.parse(getHttpResponse("repomd.xml").getEntity().getContent());
    return document.getFirstChild().getFirstChild().getTextContent();
  }

  private void assertGetRepoMdXmlReturns(final int statusCode) throws Exception {
    await("responseCode for repomd.xml").atMost(TEN_SECONDS).until(() -> downloadFile("repomd.xml") == statusCode);
  }

  private void insertScheduledRepoEntryIntoMongoDb() throws UnknownHostException {
    DBCollection collection = getRepoEntryCollection();
    collection.update(new BasicDBObject("name", reponame),
      new BasicDBObject("$set", new BasicDBObject("type", SCHEDULED.toString())),
      true, true, WriteConcern.FSYNC_SAFE);
  }

  private DBCollection getRepoEntryCollection() throws UnknownHostException {
    return mongo.getDB("rpm_db").getCollection(REPO_ENTRY_COLLECTION);
  }

  private BasicDBObject mongoRepoEntry() {
    BasicDBObject basicDBObject = new BasicDBObject();
    basicDBObject.put("name", reponame);
    basicDBObject.put("type", SCHEDULED.toString());
    return basicDBObject;
  }

  private int downloadFile(String filename) throws Exception {
    HttpResponse response = getHttpResponse(filename);
    consume(response.getEntity());
    return response.getStatusLine().getStatusCode();
  }

  private HttpResponse getHttpResponse(String filename) throws IOException {
    HttpGet get = new HttpGet(repoUrl + "/repodata/" + filename);
    return httpClient.execute(get);
  }
}
