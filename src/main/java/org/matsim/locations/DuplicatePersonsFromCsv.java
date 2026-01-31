package org.matsim.locations;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class DuplicatePersonsFromCsv {

	public static void main(String[] args) {

		String plansFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/dresden/dresden-v1.0/input/dresden-v1.0-10pct.plans.xml.gz";
		String personsCsv = "Bearbeitung/persons_with_work_in_shape10pct.csv";
		String outputPlans = "Bearbeitung/plans-with-duplicated-persons-10pct.xml";

		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

		new PopulationReader(scenario).readFile(plansFile);

		Set<Id<Person>> personsToDuplicate = readPersonIdsFromCsv(personsCsv);

		duplicatePersons(
			scenario.getPopulation(),
			scenario.getPopulation().getFactory(),
			scenario.getVehicles(),
			personsToDuplicate
		);

		PopulationUtils.writePopulation(scenario.getPopulation(), outputPlans);

		System.out.println("Fertig.");
		System.out.println("Gesamtpersonen: " + scenario.getPopulation().getPersons().size());
	}

	// CSV EINLESEN
	private static Set<Id<Person>> readPersonIdsFromCsv(String csvFile) {
		Set<Id<Person>> ids = new HashSet<>();
		try (BufferedReader reader = Files.newBufferedReader(Path.of(csvFile))) {
			reader.readLine();
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (!line.isEmpty()) {
					ids.add(Id.createPersonId(line));
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return ids;
	}

	// PERSONEN DUPLIZIEREN
	private static void duplicatePersons(
		Population population,
		PopulationFactory factory,
		Vehicles vehicles,
		Set<Id<Person>> personsToDuplicate) {

		for (Id<Person> originalId : personsToDuplicate) {

			Person original = population.getPersons().get(originalId);
			if (original == null) continue;

			// Fahrzeuge aus den Routen
			Set<Id<Vehicle>> originalVehicleIds = collectVehicleIdsFromPlans(original);

			for (int i = 1; i <= 5; i++) {

				Id<Person> newId = Id.createPersonId(originalId + "_clone" + i);
				if (population.getPersons().containsKey(newId)) continue;

				Person clone = factory.createPerson(newId);

				// Person-Attribute manuell kopieren
				original.getAttributes().getAsMap().forEach(
					(key, value) -> clone.getAttributes().putAttribute(key, value)
				);

				// Subpopulation setzen
				if (clone.getAttributes().getAttribute("subpopulation") == null) {
					clone.getAttributes().putAttribute("subpopulation", "person");
				}

				// Plans kopieren + vehicleRefId anpassen
				for (Plan plan : original.getPlans()) {
					Plan newPlan = factory.createPlan();
					PopulationUtils.copyFromTo(plan, newPlan);
					fixNetworkRouteVehicleIds(newPlan, originalId, newId);
					clone.addPlan(newPlan);

					if (plan == original.getSelectedPlan()) {
						clone.setSelectedPlan(newPlan);
					}
				}

				// Fahrzeuge klonen
				cloneVehicles(
					vehicles,
					originalVehicleIds,
					originalId,
					newId
				);

				clone.getAttributes().putAttribute("clonedFrom", originalId.toString());
				population.addPerson(clone);
			}
		}
	}

	// FAHRZEUGE AUS ROUTEN
	private static Set<Id<Vehicle>> collectVehicleIdsFromPlans(Person person) {

		Set<Id<Vehicle>> ids = new HashSet<>();

		for (Plan plan : person.getPlans()) {
			for (PlanElement pe : plan.getPlanElements()) {
				if (pe instanceof Leg leg) {
					if (leg.getRoute() instanceof NetworkRoute route) {
						if (route.getVehicleId() != null) {
							ids.add(route.getVehicleId());
						}
					}
				}
			}
		}
		return ids;
	}

	// FAHRZEUGE KLONEN
	private static void cloneVehicles(
		Vehicles vehicles,
		Set<Id<Vehicle>> originalVehicleIds,
		Id<Person> originalId,
		Id<Person> newPersonId) {

		for (Id<Vehicle> oldVehId : originalVehicleIds) {

			Vehicle oldVehicle = vehicles.getVehicles().get(oldVehId);
			if (oldVehicle == null) continue;

			Id<Vehicle> newVehId = Id.createVehicleId(
				oldVehId.toString()
					.replace(originalId.toString(), newPersonId.toString())
			);

			if (vehicles.getVehicles().containsKey(newVehId)) continue;

			Vehicle newVehicle = VehicleUtils.createVehicle(
				newVehId,
				oldVehicle.getType()
			);

			vehicles.addVehicle(newVehicle);
		}
	}

	// vehicleRefId
	private static void fixNetworkRouteVehicleIds(
		Plan plan,
		Id<Person> originalId,
		Id<Person> newPersonId) {

		for (PlanElement pe : plan.getPlanElements()) {
			if (pe instanceof Leg leg) {
				if (leg.getRoute() instanceof NetworkRoute route) {

					Id<Vehicle> oldVehId = route.getVehicleId();
					if (oldVehId != null) {
						route.setVehicleId(
							Id.createVehicleId(
								oldVehId.toString()
									.replace(originalId.toString(), newPersonId.toString())
							)
						);
					}
				}
			}
		}
	}
}
