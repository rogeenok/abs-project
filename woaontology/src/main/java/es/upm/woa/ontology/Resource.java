package es.upm.woa.ontology;

import jade.content.*;
import jade.util.leap.*;
import jade.core.*;

/**
* Protege name: Resource
* @author ontology bean generator
* @version 2019/05/22, 18:53:14
*/
public class Resource extends CellContent{ 

   /**
* Protege name: resourceType
   */
   private String resourceType;
   public void setResourceType(String value) { 
    this.resourceType=value;
   }
   public String getResourceType() {
     return this.resourceType;
   }

   /**
* Protege name: goldPercentage
   */
   private int goldPercentage;
   public void setGoldPercentage(int value) { 
    this.goldPercentage=value;
   }
   public int getGoldPercentage() {
     return this.goldPercentage;
   }

   /**
* Protege name: resourceAmount
   */
   private int resourceAmount;
   public void setResourceAmount(int value) { 
    this.resourceAmount=value;
   }
   public int getResourceAmount() {
     return this.resourceAmount;
   }

}
