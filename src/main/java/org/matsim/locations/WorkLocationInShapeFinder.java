package org.matsim.locations;

import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.gis.ShapeFileReader;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class WorkLocationInShapeFinder {

	private final Collection<Geometry> geometries = new HashSet<>();
	private final GeometryFactory geometryFactory = new GeometryFactory();

	public static void main(String[] args) {

		String plansFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/dresden/dresden-v1.0/input/dresden-v1.0-10pct.plans.xml.gz";
		String shapeFile = "input/shp/work.shp";
		String outputCsv = "Bearbeitung/persons_with_work_in_shape10pct.csv";

		var scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new PopulationReader(scenario).readFile(plansFile);

		WorkLocationInShapeFinder finder =
			new WorkLocationInShapeFinder(shapeFile);

		Set<Id<Person>> persons =
			finder.getPersonsWithWorkInShape(scenario.getPopulation());

		finder.writePersonsToCsv(persons, outputCsv);

		System.out.println("CSV geschrieben: " + outputCsv);
		System.out.println("Anzahl Personen: " + persons.size());

	}

	public WorkLocationInShapeFinder(String shapeFile) {

		Collection<SimpleFeature> features =
			ShapeFileReader.getAllFeatures(shapeFile);

		for (SimpleFeature feature : features) {
			Geometry geometry = (Geometry) feature.getDefaultGeometry();
			geometries.add(geometry);
		}
	}

	public Set<Id<Person>> getPersonsWithWorkInShape(Population population) {

		Set<Id<Person>> persons = new HashSet<>();

		for (Person person : population.getPersons().values()) {

			Plan plan = person.getSelectedPlan();

			for (PlanElement pe : plan.getPlanElements()) {

				if (pe instanceof Activity) {
					Activity act = (Activity) pe;

					if (act.getType().startsWith("work") && act.getCoord() != null) {

						Point point = coordToPoint(act.getCoord());

						if (isInsideAnyGeometry(point)) {
							persons.add(person.getId());

						}
					}
				}
			}
		}
		return persons;
	}

	private boolean isInsideAnyGeometry(Point point) {
		for (Geometry geometry : geometries) {
			if (geometry.contains(point)) {
				return true;
			}
		}
		return false;
	}

	private Point coordToPoint(Coord coord) {
		Coordinate c = new Coordinate(coord.getX(), coord.getY());
		return geometryFactory.createPoint(c);
	}
	public void writePersonsToCsv(Set<Id<Person>> persons, String outputFile) {

		try (BufferedWriter writer = Files.newBufferedWriter(Path.of(outputFile))) {

			writer.write("personId");
			writer.newLine();

			for (Id<Person> personId : persons) {
				writer.write(personId.toString());
				writer.newLine();
			}

		} catch (IOException e) {
			throw new RuntimeException("Fehler beim Schreiben der CSV-Datei", e);
		}
	}
}
