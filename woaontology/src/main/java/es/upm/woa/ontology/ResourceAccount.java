package es.upm.woa.ontology;


import jade.content.*;
import jade.util.leap.*;
import jade.core.*;

/**
* Protege name: ResourceAccount
* @author ontology bean generator
* @version 2019/05/22, 18:53:14
*/
public class ResourceAccount implements Concept {

   /**
* Protege name: wood
   */
   private int wood;
   public void setWood(int value) { 
    this.wood=value;
   }
   public int getWood() {
     return this.wood;
   }

   /**
* Protege name: stone
   */
   private int stone;
   public void setStone(int value) { 
    this.stone=value;
   }
   public int getStone() {
     return this.stone;
   }

   /**
* Protege name: food
   */
   private int food;
   public void setFood(int value) { 
    this.food=value;
   }
   public int getFood() {
     return this.food;
   }

   /**
* Protege name: gold
   */
   private int gold;
   public void setGold(int value) { 
    this.gold=value;
   }
   public int getGold() {
     return this.gold;
   }

}
