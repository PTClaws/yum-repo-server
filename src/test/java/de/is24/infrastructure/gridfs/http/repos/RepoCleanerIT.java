package de.is24.infrastructure.gridfs.http.repos;

import de.is24.infrastructure.gridfs.http.category.LocalExecutionOnly;
import de.is24.infrastructure.gridfs.http.domain.RepoEntry;
import de.is24.infrastructure.gridfs.http.domain.RepoType;
import de.is24.infrastructure.gridfs.http.domain.YumEntry;
import de.is24.infrastructure.gridfs.http.domain.yum.YumPackage;
import de.is24.infrastructure.gridfs.http.domain.yum.YumPackageLocation;
import de.is24.infrastructure.gridfs.http.domain.yum.YumPackageTime;
import de.is24.infrastructure.gridfs.http.domain.yum.YumPackageVersion;
import de.is24.infrastructure.gridfs.http.mongo.IntegrationTestContext;
import de.is24.infrastructure.gridfs.http.storage.FileDescriptor;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.List;

import static de.is24.infrastructure.gridfs.http.mongo.IntegrationTestContext.mongoTemplate;
import static de.is24.infrastructure.gridfs.http.utils.RepositoryUtils.simpleInputStream;
import static de.is24.infrastructure.gridfs.http.utils.RepositoryUtils.uniqueRepoName;
import static java.lang.System.currentTimeMillis;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertTrue;


@Category(LocalExecutionOnly.class)
public class RepoCleanerIT {
  private static final String NAME1 = "test-artifactus";
  private static final String NAME2 = "artifactus-test";
  private static final String NOARCH = "noarch";
  private static final String SRC = "src";
  private static final Integer CURRENT = getCurrent();
  private static final Integer FIVE_DAYS_AGO = getFiveDaysAgo();

  private static final YumEntry[] YUM_ENTRIES_TO_KEEP = {
    entry(NAME1, "1.0", "1", NOARCH, CURRENT, CURRENT),
    entry(NAME1, "2.0", "1", NOARCH, CURRENT, CURRENT),
    entry(NAME1, "0.1", "1", SRC, CURRENT, CURRENT),
    entry(NAME1, "0.2", "1", SRC, CURRENT, CURRENT),

    entry(NAME2, "1.1.1", "1", NOARCH, CURRENT, CURRENT),
    entry(NAME2, "1.1", "2", NOARCH, CURRENT, CURRENT),
    entry(NAME2, "1.1", "1", NOARCH, CURRENT, CURRENT)
  };

  private static final YumEntry[] YUM_ENTRIES_TO_CLEAN_UP = {
    entry(NAME2, "1.0", "1", NOARCH, CURRENT, FIVE_DAYS_AGO),
    entry(NAME2, "0.9", "1", NOARCH, CURRENT, FIVE_DAYS_AGO)
  };

  private static Integer getCurrent() {
    long current = new DateTime().withZoneRetainFields(DateTimeZone.UTC).getMillis() / 1000L ;
    return (int) current;
  }

  private static Integer getFiveDaysAgo() {
    long fiveDaysAgo = new DateTime().minusDays(5).withZoneRetainFields(DateTimeZone.UTC).getMillis() / 1000L;
    return (int) fiveDaysAgo;
  }

  @ClassRule
  public static IntegrationTestContext context = new IntegrationTestContext();
  private String reponame;
  private RepoCleaner service;

  @Before
  public void setUp() throws Exception {
    reponame = uniqueRepoName();
    service = new RepoCleaner(mongoTemplate(context.getMongo()), context.yumEntriesRepository(),
      context.fileStorageService(),
      context.repoService());
  }

  @Test
  public void cleanupRepoByMaxDaysRpms() throws Exception {
    givenRepoEntryWithMaxKeep(0);
    givenRepoEntryWithMaxDays(3);
    givenRepoWithFilesToClean();

    long startTime = currentTimeMillis();

    assertThat(service.cleanup(reponame), is(true));

    assertThatItemsHasBeenCleanedUp();
    assertThatRepoEntryIsMarkedAsModified(startTime);
    assertThatGridFsFileIsMarkedAsDeleted();
  }

  @Test
  public void cleanupRepoByMaxKeepRpms() throws Exception {
    givenRepoEntryWithMaxDays(0);
    givenRepoEntryWithMaxKeep(3);
    givenRepoWithFilesToClean();

    long startTime = currentTimeMillis();

    assertThat(service.cleanup(reponame), is(true));

    assertThatItemsHasBeenCleanedUp();
    assertThatRepoEntryIsMarkedAsModified(startTime);
    assertThatGridFsFileIsMarkedAsDeleted();
  }

  @Test
  public void doNothingIfMaxKeepRpmsAndMaxDaysRpmsIsZero() throws Exception {
    givenRepoEntryWithMaxKeep(0);
    givenRepoEntryWithMaxDays(0);
    givenRepoWithFilesToClean();

    assertThat(service.cleanup(reponame), is(false));

    assertThatNoItemsHasBeenCleanedUp();
  }

  private void assertThatRepoEntryIsMarkedAsModified(long startTime) {
    RepoEntry repoEntry = context.repoEntriesRepository().findFirstByName(reponame);
    assertThat(repoEntry.getLastModified().getTime(), greaterThan(startTime));
  }

  private void assertThatGridFsFileIsMarkedAsDeleted() {
    for (YumEntry entryToDelete : YUM_ENTRIES_TO_CLEAN_UP) {
      assertTrue(context.fileStorageService().findBy(new FileDescriptor(entryToDelete)).isMarkedAsDeleted());
    }
  }

  private void assertThatItemsHasBeenCleanedUp() {
    assertEntriesExist(YUM_ENTRIES_TO_KEEP);
    for (YumEntry entry : YUM_ENTRIES_TO_CLEAN_UP) {
      assertEntryDoesNotExist(entry);
    }
  }

  private void assertThatNoItemsHasBeenCleanedUp() {
    assertEntriesExist(YUM_ENTRIES_TO_KEEP);
    assertEntriesExist(YUM_ENTRIES_TO_CLEAN_UP);
  }

  private void assertEntriesExist(YumEntry[] entries) {
    for (YumEntry entry : entries) {
      assertEntryExists(entry);
    }
  }

  private void assertEntryDoesNotExist(YumEntry entry) {
    assertThat(findVersionsFor(entry.getYumPackage().getName()), not(hasItem(entry.getYumPackage().getVersion())));
  }

  private void assertEntryExists(YumEntry entry) {
    assertThat(findVersionsFor(entry.getYumPackage().getName()), hasItem(entry.getYumPackage().getVersion()));
  }

  private List<YumPackageVersion> findVersionsFor(String artifactName) {
    List<YumEntry> entries = context.yumEntriesRepository().findByRepoAndYumPackageName(reponame, artifactName);
    return entries.stream().map(YumEntry::getYumPackage).map(YumPackage::getVersion).collect(toList());
  }

  private void givenRepoEntryWithMaxKeep(int maxKeepRpms) {
    RepoEntry repoEntry = context.repoService().ensureEntry(reponame, (RepoType) null);
    repoEntry.setMaxKeepRpms(maxKeepRpms);
    context.repoEntriesRepository().save(repoEntry);
  }

  private void givenRepoEntryWithMaxDays(int maxDaysRpms) {
    RepoEntry repoEntry = context.repoService().ensureEntry(reponame, (RepoType) null);
    repoEntry.setMaxDaysRpms(maxDaysRpms);
    context.repoEntriesRepository().save(repoEntry);
  }

  private void givenRepoWithFilesToClean() {
    for (YumEntry entry : YUM_ENTRIES_TO_KEEP) {
      entry.setRepo(reponame);
      context.yumEntriesRepository().save(entry);
    }

    for (YumEntry entry : YUM_ENTRIES_TO_CLEAN_UP) {
      entry.setRepo(reponame);
      context.yumEntriesRepository().save(entry);

      context.gridFsTemplate().store(simpleInputStream(), entry.getFullRpmFilename()).save();
    }
  }

  private static YumEntry entry(String name, String version, String release, String arch, Integer file, Integer build) {
    YumPackage yumPackage = new YumPackage();
    yumPackage.setName(name);
    yumPackage.setArch(arch);
    yumPackage.setVersion(packageVersion(version, release));
    yumPackage.setTime(packageTime(file, build));

    YumPackageLocation location = new YumPackageLocation();
    location.setHref(arch + "/" + name + "-" + version + "-" + release + "." + arch + ".rpm");
    yumPackage.setLocation(location);
    return new YumEntry(null, null, yumPackage);
  }

  private static YumPackageTime packageTime(Integer file, Integer build) {
    YumPackageTime packageTime = new YumPackageTime();
    packageTime.setFile(file);
    packageTime.setBuild(build);
    return packageTime;
  }

  private static YumPackageVersion packageVersion(String version, String release) {
    YumPackageVersion packageVersion = new YumPackageVersion();
    packageVersion.setEpoch(0);
    packageVersion.setVer(version);
    packageVersion.setRel(release);
    return packageVersion;
  }
}
