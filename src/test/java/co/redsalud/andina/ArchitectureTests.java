package co.redsalud.andina;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;

/** Build-breaking rules next to the module checks of ModularityTests. */
class ArchitectureTests {

	JavaClasses production = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages("co.redsalud.andina");

	@Test
	void noEntityRelationshipsSoNoModuleCanJoinAnotherModulesTables() {
		noFields().should().beAnnotatedWith(ManyToOne.class)
				.orShould().beAnnotatedWith(OneToMany.class)
				.orShould().beAnnotatedWith(OneToOne.class)
				.orShould().beAnnotatedWith(ManyToMany.class)
				.because("modules reference each other by id; a JPA relationship would cross a schema boundary")
				.check(production);
	}

	@Test
	void entitiesLiveInInternalPackagesSoTheyAreNeverPartOfAModuleApi() {
		classes().that().areAnnotatedWith(Entity.class).should().resideInAPackage("..internal..").check(production);
	}

	@Test
	void controllersLiveOnlyInTheApiModule() {
		classes().that().areAnnotatedWith(RestController.class).should().resideInAPackage("co.redsalud.andina.api..").check(production);
	}
}
