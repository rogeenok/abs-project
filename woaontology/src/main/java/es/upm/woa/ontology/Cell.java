package es.upm.woa.ontology;


import jade.content.*;
import jade.util.leap.*;
import jade.core.*;

/**
* Protege name: Cell
* @author ontology bean generator
* @version 2019/05/22, 18:53:14
*/
public class Cell implements Concept {

   /**
* Protege name: X
   */
   private int x;
   public void setX(int value) { 
    this.x=value;
   }
   public int getX() {
     return this.x;
   }

   /**
* Protege name: Y
   */
   private int y;
   public void setY(int value) { 
    this.y=value;
   }
   public int getY() {
     return this.y;
   }

   /**
* Protege name: Content
   */
   private CellContent content;
   public void setContent(CellContent value) { 
    this.content=value;
   }
   public CellContent getContent() {
     return this.content;
   }

}
