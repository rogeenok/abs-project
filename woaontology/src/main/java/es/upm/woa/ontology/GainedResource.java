package es.upm.woa.ontology;


import jade.content.*;
import jade.util.leap.*;
import jade.core.*;

/**
* Protege name: GainedResource
* @author ontology bean generator
* @version 2019/05/22, 18:53:14
*/
public class GainedResource implements Concept {

   /**
* Protege name: resourceName
   */
   private String resourceName;
   public void setResourceName(String value) { 
    this.resourceName=value;
   }
   public String getResourceName() {
     return this.resourceName;
   }

   /**
* Protege name: amount
   */
   private int amount;
   public void setAmount(int value) { 
    this.amount=value;
   }
   public int getAmount() {
     return this.amount;
   }

}
