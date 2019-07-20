package es.upm.woa.group4.protocol;

import es.upm.woa.ontology.Cell;
import es.upm.woa.ontology.GameOntology;
import jade.content.onto.BasicOntology;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.content.onto.ReflectiveIntrospector;
import jade.content.schema.AgentActionSchema;
import jade.content.schema.ConceptSchema;
import jade.content.schema.ObjectSchema;
import jade.content.schema.TermSchema;

import static es.upm.woa.ontology.GameOntology.*;

public class OwnOntology extends Ontology {

    //NAME
    public static final String ONTOLOGY_NAME = "own addtions";
    private static ReflectiveIntrospector introspect = new ReflectiveIntrospector();
    private static Ontology theInstance = new OwnOntology();
    public static Ontology getInstance() {
        return theInstance;
    }

    //VOCABULARY
    public static final String INITIATETRIBES="InitiateTribes";
    public static final String INITIATETRIBES_TRIBELIST="tribeList";
    public static final String WELCOME_UNIT="WelcomeUnit";
    public static final String WELCOME_UNIT_TRIBEAID="tribeAID";
    public static final String WELCOME_UNIT_STARTINGPOSITION="startingPosition";

    private OwnOntology() {
        super(ONTOLOGY_NAME, BasicOntology.getInstance());
        try {

            // Concepts
            ConceptSchema cellSchema = new ConceptSchema(CELL);
            add(cellSchema, Cell.class);

            // Agent Actions
            AgentActionSchema initiateTribesSchema = new AgentActionSchema(INITIATETRIBES);
            add(initiateTribesSchema, InitiateTribes.class);
            AgentActionSchema welcomeUnitSchema = new AgentActionSchema(WELCOME_UNIT);
            add(welcomeUnitSchema, WelcomeUnit.class);

            // Fields
            cellSchema.add(CELL_X, (TermSchema)getSchema(BasicOntology.INTEGER), ObjectSchema.MANDATORY);
            cellSchema.add(CELL_Y, (TermSchema)getSchema(BasicOntology.INTEGER), ObjectSchema.MANDATORY);
            initiateTribesSchema.add(INITIATETRIBES_TRIBELIST, (ConceptSchema)getSchema(BasicOntology.AID),1, ObjectSchema.UNLIMITED);
            welcomeUnitSchema.add(WELCOME_UNIT_TRIBEAID, (TermSchema) getSchema(BasicOntology.AID), ObjectSchema.MANDATORY);
            welcomeUnitSchema.add(WELCOME_UNIT_STARTINGPOSITION, cellSchema, ObjectSchema.MANDATORY);

        } catch (OntologyException e) {
            e.printStackTrace();
        }
    }
}
