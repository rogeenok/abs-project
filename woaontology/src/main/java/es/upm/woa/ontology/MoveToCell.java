package es.upm.woa.ontology;


import jade.content.*;
import jade.util.leap.*;
import jade.core.*;

/**
* Protege name: MoveToCell
* @author ontology bean generator
* @version 2019/05/22, 18:53:14
*/
public class MoveToCell implements AgentAction {

   /**
* Protege name: targetDirection
   */
   private int targetDirection;
   public void setTargetDirection(int value) { 
    this.targetDirection=value;
   }
   public int getTargetDirection() {
     return this.targetDirection;
   }

   /**
   * This property shall only be filled in the INFORM informative, after the protocol has been completed.
* Protege name: newlyArrivedCell
   */
   private Cell newlyArrivedCell;
   public void setNewlyArrivedCell(Cell value) { 
    this.newlyArrivedCell=value;
   }
   public Cell getNewlyArrivedCell() {
     return this.newlyArrivedCell;
   }

}
