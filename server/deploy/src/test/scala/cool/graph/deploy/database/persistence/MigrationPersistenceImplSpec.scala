package cool.graph.deploy.database.persistence

import cool.graph.deploy.database.tables.Tables
import cool.graph.deploy.specutils.DeploySpecBase
import cool.graph.shared.models.Migration
import org.scalatest.{FlatSpec, Matchers}
import slick.jdbc.MySQLProfile.api._

class MigrationPersistenceImplSpec extends FlatSpec with Matchers with DeploySpecBase {

  val migrationPersistence = testDependencies.migrationPersistence
  val projectPersistence   = testDependencies.projectPersistence

  ".create()" should "store the migration in the db and increment the revision accordingly" in {
    val project = setupProject(basicTypesGql)
    assertNumberOfRowsInMigrationTable(2)

    val savedMigration = migrationPersistence.create(project, Migration.empty(project)).await()
    assertNumberOfRowsInMigrationTable(3)
    savedMigration.revision shouldEqual 3
  }

  ".loadAll()" should "return all migrations for a project" in {
    val project = setupProject(basicTypesGql)

    // 1 applied, 2 unapplied migrations (+ 2 from setup)
    migrationPersistence.create(project, Migration.empty(project).copy(hasBeenApplied = true)).await
    migrationPersistence.create(project, Migration.empty(project)).await
    migrationPersistence.create(project, Migration.empty(project)).await

    val migrations = migrationPersistence.loadAll(project.id).await
    migrations should have(size(5))
  }

  ".getUnappliedMigration()" should "return an unapplied migration from any project" in {
    val project  = setupProject(basicTypesGql)
    val project2 = setupProject(basicTypesGql)

    // 2 unapplied migrations
    migrationPersistence.create(project, Migration.empty(project)).await
    migrationPersistence.create(project2, Migration.empty(project2)).await

    val unapplied = migrationPersistence.getUnappliedMigration().await()
    unapplied.isDefined shouldEqual true

    migrationPersistence.markMigrationAsApplied(unapplied.get.migration).await()
    val unapplied2 = migrationPersistence.getUnappliedMigration().await()

    unapplied2.isDefined shouldEqual true
    unapplied2.get.migration.projectId shouldNot equal(unapplied.get.migration.projectId)

    migrationPersistence.markMigrationAsApplied(unapplied2.get.migration).await()
    migrationPersistence.getUnappliedMigration().await().isDefined shouldEqual false
  }

  ".markMigrationAsApplied()" should "mark a migration as applied (duh)" in {
    val project          = setupProject(basicTypesGql)
    val createdMigration = migrationPersistence.create(project, Migration.empty(project)).await

    migrationPersistence.markMigrationAsApplied(createdMigration).await
    migrationPersistence.getLastMigration(project.id).await.get.revision shouldEqual createdMigration.revision
  }

  ".getLastMigration()" should "get the last migration applied to a project" in {
    val project = setupProject(basicTypesGql)
    migrationPersistence.getLastMigration(project.id).await.get.revision shouldEqual 2
  }

  ".getNextMigration()" should "get the next migration to be applied to a project" in {
    val project          = setupProject(basicTypesGql)
    val createdMigration = migrationPersistence.create(project, Migration.empty(project)).await

    migrationPersistence.getNextMigration(project.id).await.get.revision shouldEqual createdMigration.revision
  }
  def assertNumberOfRowsInMigrationTable(count: Int): Unit = {
    val query = Tables.Migrations.size
    internalDb.run(query.result) should equal(count)
  }
}