package es.upm.woa.group4.util;

import es.upm.woa.ontology.*;
import jade.core.AID;

public class CellUtil {

    public static Cell createBuildingCell(AID ownerAID, String type, Integer x, Integer y) {
        Building building = new Building();
        building.setType(type);
        building.setOwner(ownerAID);
        return createCell(building, x, y);
    }

    public static Cell createGroundCell(Integer x, Integer y) {
        return createCell(new Ground(), x, y);
    }

    public static Cell createResourceCell(String type, Integer gold, Integer amount, Integer x, Integer y) {
        Resource resource = new Resource();
        resource.setResourceType(type);
        resource.setGoldPercentage(gold);
        resource.setResourceAmount(amount);
        return createCell(resource, x, y);
    }

    public static Boolean hasSameCoordinates(Cell cell1, Cell cell2) {
        return cell1.getX() == cell2.getX() && cell1.getY() == cell2.getY();
    }

    private static Cell createCell(CellContent content, Integer x, Integer y) {
        Cell cell = new Cell();
        cell.setContent(content);
        cell.setX(x);
        cell.setY(y);
        return cell;
    }

    public static Cell calculateTargetCell(Cell senderCell, int direction,
                                            int mapHeight, int mapWidth) {
        // translation vectors are
        // 1 ==> [-2;0]
        // 2 ==> [-1;+1]
        // 3 ==> [+1;+1]
        // 4 ==> [+2;0]
        // 5 ==> [+1;-1]
        // 6 ==> [-1;-1]

        Cell targetCell = CellUtil.createGroundCell(senderCell.getX(), senderCell.getY());
        switch (direction) {
            case 1: {
                targetCell.setX(targetCell.getX() - 2);
                break;
            }
            case 2: {
                targetCell.setX(targetCell.getX() - 1);
                targetCell.setY(targetCell.getY() + 1);
                break;
            }
            case 3: {
                targetCell.setX(targetCell.getX() + 1);
                targetCell.setY(targetCell.getY() + 1);
                break;
            }
            case 4: {
                targetCell.setX(targetCell.getX() + 2);
                break;
            }
            case 5: {
                targetCell.setX(targetCell.getX() + 1);
                targetCell.setY(targetCell.getY() - 1);
                break;
            }
            case 6: {
                targetCell.setX(targetCell.getX() - 1);
                targetCell.setY(targetCell.getY() - 1);
                break;
            }
            default: {
                targetCell = null;
                break;
            }
        }

        if (targetCell == null)
            return  targetCell;

        // check the map dimensions
        if (targetCell.getX() < 1) {
            targetCell.setX(mapHeight + targetCell.getX());
        } else if (targetCell.getX() > mapHeight) {
            targetCell.setX(targetCell.getX() - mapHeight);
        }
        if (targetCell.getY() < 1) {
            targetCell.setY(mapWidth + targetCell.getY());
        } else if (targetCell.getY() > mapWidth) {
            targetCell.setY(targetCell.getY() - mapWidth);
        }

        return targetCell;
    }
}