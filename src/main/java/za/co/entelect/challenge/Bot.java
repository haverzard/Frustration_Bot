package za.co.entelect.challenge;

import za.co.entelect.challenge.entities.*;
import za.co.entelect.challenge.enums.BuildingType;
import za.co.entelect.challenge.enums.PlayerType;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.lang.Math;

import static za.co.entelect.challenge.enums.BuildingType.ATTACK;
import static za.co.entelect.challenge.enums.BuildingType.DEFENSE;
import static za.co.entelect.challenge.enums.BuildingType.ENERGY;

public class Bot {
    private static final String NOTHING_COMMAND = "";
    private GameState gameState;
    private GameDetails gameDetails;
    private int gameWidth;
    private int gameHeight;
    private Player myself;
    private Player opponent;
    private List<Building> buildings;
    private List<Missile> missiles;
    private List<Integer> myTotal;
    private List<Integer> enTotal;
    private List<List<List<Integer>>> myLaneInfo;
    private List<List<List<Integer>>> enLaneInfo;
    //info tiap lane [data_turret,data_wall,data_energy,data_empty,data_missiles,data_jumlah]
    //data jumlah [turret,wall,energy,missiles,empty]

    /**
     * Constructor
     *
     * @param gameState the game state
     **/
    public Bot(GameState gameState) {
        this.gameState = gameState;
        gameDetails = gameState.getGameDetails();
        gameWidth = gameDetails.mapWidth;
        gameHeight = gameDetails.mapHeight;
        myself = gameState.getPlayers().stream().filter(p -> p.playerType == PlayerType.A).findFirst().get();
        opponent = gameState.getPlayers().stream().filter(p -> p.playerType == PlayerType.B).findFirst().get();
        buildings = gameState.getGameMap().stream()
                .flatMap(c -> c.getBuildings().stream())
                .collect(Collectors.toList());

        missiles = gameState.getGameMap().stream()
                .flatMap(c -> c.getMissiles().stream())
                .collect(Collectors.toList());
        myTotal = new ArrayList<Integer>(){{add(0);add(0);add(0);add(0);add(0);add(0);}};
        enTotal = new ArrayList<Integer>(){{add(0);add(0);add(0);add(0);add(0);add(0);}};
        myLaneInfo = new ArrayList<List<List<Integer>>>();
        enLaneInfo = new ArrayList<List<List<Integer>>>();
        for (int i = 0; i < gameHeight; i++) {
            myLaneInfo.add(getLaneInfoFromPlayer(PlayerType.A,i));
            enLaneInfo.add(getLaneInfoFromPlayer(PlayerType.B,i));
        }
    }

    /**
     * Run
     *
     * @return the result
     **/
    public String run() {
        // Check if ironCurtain is available
        if (myself.ironCurtainAvailable && myself.energy >= 120 
            && (enTotal.get(0) >= 8 || opponent.isIronCurtainActive || this.myTotal.get(0) < this.enTotal.get(0))) {
            return buildIC();
        } else if (myself.isIronCurtainActive && canBuyTurret()) {
            return buildTurret();
        //} else if (opponent.health < 30 && myself.health > opponent.health && myself.energy >= 120) {
        //    return flex(); 
        } else {
            // Check if there is any enemy turret
            if (myTotal.get(0) > enTotal.get(0)+2 && (myself.health > 50 || myself.health > opponent.health) && checkGreedyEnergy() && canBuyEnergy()) {
                return buildEnergy();
            }
            else if (myTotal.get(3) > 0 || myTotal.get(0) > 0) {
                //if (canBuyWall() && (enTotal.get(2) >= myTotal.get(2) || myTotal.get(0) > enTotal.get(0)+1))
                if (canBuyWall() && enTotal.get(2)+1 < myTotal.get(2) &&  enTotal.get(0)+1 < myTotal.get(0)) 
                    return defendRow();
                else if (canBuyTurret()) return buildTurret();
                return doNothingCommand();
            } 
            // Focus Money?
            else if (checkGreedyEnergy() && canBuyEnergy()) {
                return buildEnergy();
            } 
            // Focus Attack?
            else if (canBuyTurret()) {
                return buildTurret();
            } else {
                return doNothingCommand();
            }
        }
        // Maybe Defense?
    }

    /**
     * Build random building
     *
     * @return the result
     **/

    private boolean checkGreedyEnergy() {
        return myTotal.get(2) < 10 || (myTotal.get(2) < 13 && myTotal.get(2) <= enTotal.get(2));
    }


    private String buildRandom() {
        List<CellStateContainer> emptyCells = gameState.getGameMap().stream()
                .filter(c -> c.getBuildings().size() == 0 && c.x < (gameWidth / 2))
                .collect(Collectors.toList());

        if (emptyCells.isEmpty()) {
            return doNothingCommand();
        }

        CellStateContainer randomEmptyCell = getRandomElementOfList(emptyCells);
        BuildingType randomBuildingType = getRandomElementOfList(Arrays.asList(BuildingType.values()));

        if (!canAffordBuilding(randomBuildingType)) {
            return doNothingCommand();
        }

        return randomBuildingType.buildCommand(randomEmptyCell.x, randomEmptyCell.y);
    }
    private String buildEnergy() {
        List<Integer> emptyCellsPos0 = new ArrayList<Integer>();
        List<Integer> emptyCellsPos1 = new ArrayList<Integer>();
        for(int i = 0; i < gameHeight; i++) {
            if (myLaneInfo.get(i).get(4).isEmpty()) {
                if (myLaneInfo.get(i).get(3).contains(0)) {
                    emptyCellsPos0.add(i);
                }
                if (myLaneInfo.get(i).get(3).contains(1)) {
                    emptyCellsPos1.add(i);
                }
            }
        }
        if (!emptyCellsPos0.isEmpty()) {
            return ENERGY.buildCommand(0,getRandomElementOfList(emptyCellsPos0));
        } else if (!emptyCellsPos0.isEmpty()) {
            return ENERGY.buildCommand(1,getRandomElementOfList(emptyCellsPos1));
        } else if (canBuyTurret()) {
            return buildTurret();
        } else {
            return doNothingCommand();
        }
    }

    private String buildIC() {
        List<Integer> emptyCellsPos = new ArrayList<Integer>();
        for(int i = 0; i < gameHeight; i++) {
            if (!myLaneInfo.get(i).get(3).isEmpty()) {
                emptyCellsPos.add(i);
            }
        }
        if (emptyCellsPos.isEmpty()) {
            return doNothingCommand();
        }
        int y = getRandomElementOfList(emptyCellsPos);
        return Integer.toString(getRandomElementOfList(myLaneInfo.get(y).get(3)))+","+Integer.toString(y)+",5";
    }

    private String buildTurret() {
        List<Integer> emptyCellsPos = new ArrayList<Integer>();
        List<Integer> emptyCellsPos2 = new ArrayList<Integer>();
        List<Integer> emptyCellsPos3 = new ArrayList<Integer>();
        List<Integer> emptyCellsPos4 = new ArrayList<Integer>();
        List<Integer> emptyCellsPos5 = new ArrayList<Integer>();
        for(int i = 0; i < gameHeight; i++) {
            if (!myLaneInfo.get(i).get(3).isEmpty() && (!myLaneInfo.get(i).get(3).contains(7) ||
                    myLaneInfo.get(i).get(5).get(4) > 1)) {
                emptyCellsPos.add(i);
                if (!enLaneInfo.get(i).get(0).isEmpty()) {
                    emptyCellsPos4.add(i);
                    if (!myLaneInfo.get(i).get(2).isEmpty()) {
                        emptyCellsPos5.add(i);
                    }
                }
                if (!myLaneInfo.get(i).get(1).isEmpty()) {
                    emptyCellsPos2.add(i);
                    if (!enLaneInfo.get(i).get(0).isEmpty()) {
                        emptyCellsPos3.add(i);
                    }
                } else if (myLaneInfo.get(i).get(0).isEmpty() && enLaneInfo.get(i).get(0).isEmpty() && !enLaneInfo.get(i).get(2).isEmpty() && enTotal.get(5) <= 2) {
                    return ATTACK.buildCommand(myLaneInfo.get(i).get(3).get(myLaneInfo.get(i).get(5).get(4)-2),i);
                }
            }
        }
        if (emptyCellsPos.isEmpty()) {
            return doNothingCommand();
        }
        int y;
        if (!emptyCellsPos4.isEmpty() && enTotal.get(5) > 2) {
            if (!emptyCellsPos5.isEmpty()) {
                y = getRandomElementOfList(emptyCellsPos5);
                if (myLaneInfo.get(y).get(5).get(3) > 0) {
                    int x;
                    x = myLaneInfo.get(y).get(4).get(0);
                    while (x >= 0 && !myLaneInfo.get(y).get(3).contains(x)) {
                        x -= 1;
                    }
                    if (x >= 0) {
                        return ATTACK.buildCommand(x,y);
                    }
                }
                int t = myLaneInfo.get(y).get(3).get(myLaneInfo.get(y).get(5).get(4)-1);
                if (t == 7) return ATTACK.buildCommand(myLaneInfo.get(y).get(3).get(myLaneInfo.get(y).get(5).get(4)-2),y);
                return ATTACK.buildCommand(t,y);
            } else {
                y = getRandomElementOfList(emptyCellsPos4);
            }
        } else if (!emptyCellsPos3.isEmpty()) {
            int max = 0;
            int c = 0;
            for (int i = 1; i < emptyCellsPos3.size(); i++) {
                if (enLaneInfo.get(emptyCellsPos3.get(i)).get(5).get(0) > enLaneInfo.get(emptyCellsPos3.get(max)).get(5).get(0)) {
                    max = i;
                } else if 
                    (enLaneInfo.get(emptyCellsPos3.get(i)).get(5).get(0) == enLaneInfo.get(emptyCellsPos3.get(max)).get(5).get(0)) {
                    c++;
                }
            }
            if (max == 0 && c > 0) {
                y = getRandomElementOfList(emptyCellsPos3);   
            } else {
                y = emptyCellsPos3.get(max);
            }
        } else if (!emptyCellsPos2.isEmpty()) {
            y = getRandomElementOfList(emptyCellsPos2);
        } else {
            y = getRandomElementOfList(emptyCellsPos);
        }
        int t = myLaneInfo.get(y).get(3).get(myLaneInfo.get(y).get(5).get(4)-1);
        if (t == 7) return ATTACK.buildCommand(myLaneInfo.get(y).get(3).get(myLaneInfo.get(y).get(5).get(4)-2),y);
        return ATTACK.buildCommand(t,y);
    }

    /**
     * Has enough energy for most expensive building
     *
     * @return the result
     **/
    private boolean hasEnoughEnergyForMostExpensiveBuilding() {
        return gameDetails.buildingsStats.values().stream()
                .filter(b -> b.price <= myself.energy)
                .toArray()
                .length == 3;
    }
    private boolean canBuyEnergy() {
        return gameDetails.buildingsStats.get(ENERGY).price <= myself.energy;
    }
    private boolean canBuyTurret() {
        return gameDetails.buildingsStats.get(ATTACK).price <= myself.energy;
    }
    private boolean canBuyWall() {
        return gameDetails.buildingsStats.get(DEFENSE).price <= myself.energy;
    }

    /**
     * Defend row
     *
     * @return the result
     **/
    private String defendRow() {
        for (int i = 0; i < gameHeight; i++) {
            boolean opponentAttacking = getAnyBuildingsForPlayer(PlayerType.B, b -> b.buildingType == ATTACK, i);
            if (opponentAttacking && myLaneInfo.get(i).get(1).isEmpty() && canAffordBuilding(ATTACK)) {
                return DEFENSE.buildCommand(7,i);
            }
        }
        return buildTurret();
    }

    /**
     * Do nothing command
     *
     * @return the result
     **/
    private String doNothingCommand() {
        return NOTHING_COMMAND;
    }

    /**
     * Place building in row
     *
     * @param buildingType the building type
     * @param y            the y
     * @return the result
     **/
    private String placeBuildingInRow(BuildingType buildingType, int y) {
        List<CellStateContainer> emptyCells = gameState.getGameMap().stream()
                .filter(c -> c.getBuildings().isEmpty()
                        && c.y == y
                        && c.x < (gameWidth / 2) - 1)
                .collect(Collectors.toList());

        if (emptyCells.isEmpty()) {
            return buildRandom();
        }

        CellStateContainer randomEmptyCell = getRandomElementOfList(emptyCells);
        return buildingType.buildCommand(randomEmptyCell.x, randomEmptyCell.y);
    }

    /**
     * Get random element of list
     *
     * @param list the list < t >
     * @return the result
     **/
    private <T> T getRandomElementOfList(List<T> list) {
        return list.get((new Random()).nextInt(list.size()));
    }

    private boolean getAnyBuildingsForPlayer(PlayerType playerType, Predicate<Building> filter, int y) {
        return buildings.stream()
                .filter(b -> b.getPlayerType() == playerType
                        && b.getY() == y)
                .anyMatch(filter);
    }

    private List<List<Integer>> getLaneInfoFromPlayer(PlayerType playerType, int y) {
        List<List<Integer>> res = new ArrayList<List<Integer>>();
        List<CellStateContainer> csc = gameState.getGameMap().stream()
                .filter(c -> c.y == y)
                .collect(Collectors.toList());
        List<Integer> turret = new ArrayList<Integer>(), wall = new ArrayList<Integer>(), energy = new ArrayList<Integer>(), empty = new ArrayList<Integer>(), missile = new ArrayList<Integer>(), total = new ArrayList<Integer>();
        int sturret = 0, swall = 0, senergy = 0, sempty = 0, smissile = 0, c = 0;
        
        if (playerType == PlayerType.B) {
            c = gameWidth-1;
        }
        for (int i = 0; i < gameWidth/2; i++) {
            List<Building> a = csc.get(Math.abs(i-c)).getBuildings();
            List<Missile> b = csc.get(Math.abs(i-c)).getMissiles();
            BuildingType t = null;
            if (!a.isEmpty()) {
                t = a.get(0).buildingType;
            }
            if (t == ATTACK) {
                turret.add(Math.abs(i-c));
                sturret += 1; 
            } else if (t == DEFENSE) {
                wall.add(Math.abs(i-c)); 
                swall += 1;
            } else if (t == ENERGY) {
                energy.add(Math.abs(i-c));
                senergy += 1; 
            } else if (t == null) {
                if (b.stream().anyMatch(mis -> mis.getPlayerType() == PlayerType.B)) {  
                    missile.add(Math.abs(i-c));
                    smissile += 1;
                } else {
                    empty.add(Math.abs(i-c));
                    sempty += 1; 
                }
            } 
        }
        if (playerType == PlayerType.A) {
            myTotal.set(0,myTotal.get(0)+sturret);
            myTotal.set(1,myTotal.get(1)+swall);
            myTotal.set(2,myTotal.get(2)+senergy);
            myTotal.set(4,myTotal.get(4)+sempty);
            if (sturret > 0) {
                myTotal.set(5,myTotal.get(5)+1);
            }
        } else {
            enTotal.set(0,enTotal.get(0)+sturret);
            enTotal.set(1,enTotal.get(1)+swall);
            enTotal.set(2,enTotal.get(2)+senergy);
            enTotal.set(4,enTotal.get(4)+sempty);
            if (sturret > 0) {
                enTotal.set(5,enTotal.get(5)+1);
            }
        }
        myTotal.set(3,myTotal.get(3)+smissile);
        total.add(sturret); 
        total.add(swall); 
        total.add(senergy); 
        total.add(smissile); 
        total.add(sempty); 
        res.add(turret);
        res.add(wall);
        res.add(energy);
        res.add(empty);
        res.add(missile);
        res.add(total);

        return res;
    }


    /**
     * Can afford building
     *
     * @param buildingType the building type
     * @return the result
     **/
    private boolean canAffordBuilding(BuildingType buildingType) {
        return myself.energy >= gameDetails.buildingsStats.get(buildingType).price;
    }
    private String tesla(int x, int y) {
        return Integer.toString(x)+","+Integer.toString(y)+",4";
    }
}
