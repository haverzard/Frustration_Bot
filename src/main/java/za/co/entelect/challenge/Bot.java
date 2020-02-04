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
    /* 
        myTotal & enTotal 
        - mengandung informasi jumlah setiap bangunan dengan struktur di bawah
        - myTotal punya kita, enTotal punya musuh
        - structure:
            [turret,wall,energy,missiles,empty]
    */
    private List<Integer> myTotal;
    private List<Integer> enTotal;
    /* 
        myLaneInfo dan enLaneInfo 
        - mengandung informasi letak bangunan dan jumlahnya sesuai lanenya
        - structure: 
            - list dari struktur [data_turret,data_wall,data_energy,data_empty,data_missiles,data_jumlah]
              dengan ukuran sebesar lane yaitu 8 (masing-masing data adalah list)
            - data_jumlah memiliki struktur seperti myTotal & enTotal
        - misal ingin memperoleh list letak bangunan turret musuh pada lane 0,
          maka kodenya "enLaneInfo.get(0).get(0)"
    */
    private List<List<List<Integer>>> myLaneInfo;
    private List<List<List<Integer>>> enLaneInfo;


    /*
        Constructor Bot
        Informasi diperoleh dari file state.json dengan menggunakan library gson
        Lalu dicari informasi per lane nya dengan metode getLaneInfoFromPlayer
    */
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

    /*
        Proses Seleksi Program
    */
    public String run() {
        ///// tambah health INGET WOI
        // Check if ironCurtain is available to use and GreedyIronCurtain is available
        if (myself.ironCurtainAvailable && myself.energy >= 120 && checkGreedyIronCurtain()) {
            return buildIC();
        } else if (myself.isIronCurtainActive && canBuyTurret()) {
            return buildTurret();
        } else {
            // Check if GreedyWinRate is available
            if (checkGreedyWinRate() && checkGreedyEnergy() && canBuyEnergy()) {
                return buildEnergy();
            }
            // Check if there is an attack..
            else if (myTotal.get(3) > 0 || myTotal.get(0) > 0) {
                // Check if GreedyDefense is available
                if (canBuyWall() && checkGreedyDefense()) 
                    return defendRow();
                else if (canBuyTurret()) return buildTurret();
                return doNothingCommand();
            } 
            // Check if GreedyEnergy is available
            else if (checkGreedyEnergy() && canBuyEnergy()) {
                return buildEnergy();
            } 
            // Just Attack if there is no greedy or do nothing if there is no energy
            else if (canBuyTurret()) {
                return buildTurret();
            } else {
                return doNothingCommand();
            }
        }
    }

    /* 
        GreedyEnergy: get 10 energy buildings first or build energy buildings as long as enemy builds energy
                      and my energy buildings are less than 13
    */
    private boolean checkGreedyEnergy() {
        return myTotal.get(2) < 10 || (myTotal.get(2) < 13 && myTotal.get(2) <= enTotal.get(2));
    }

    /* 
        GreedyIronCurtain: either
            - health is low
            - enemy's turrets are more than 8
            - enemy activates iron curtain
            - my turrets are less than enemy's turrets
    */
    private boolean checkGreedyIronCurtain() {
        return (myself.health < 30 || enTotal.get(0) >= 8 || opponent.isIronCurtainActive || this.myTotal.get(0) < this.enTotal.get(0));
    }

    /*
        GreedyWinRate: my turrets are more than enemy's turrets and either
            - my health is more than half max
            - my health is more than enemy's health
    */
    private boolean checkGreedyWinRate() {
        return myTotal.get(0) > enTotal.get(0)+2 && (myself.health > 50 || myself.health > opponent.health);
    }

    /*
        GreedyDefense: my turrets are more than enemy's turrets 
                       and my energy buildings are more than enemy's energy buildings
    */
    private boolean checkGreedyDefense() {
        return enTotal.get(2)+1 < myTotal.get(2) &&  enTotal.get(0)+1 < myTotal.get(0);
    }

    /*
        Build Energy only on the last (behind) 2 columns and on a lane/row with no missiles
        If there is no available place, just attack or wait
    */
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
        } else if (!emptyCellsPos1.isEmpty()) {
            return ENERGY.buildCommand(1,getRandomElementOfList(emptyCellsPos1));
        } else if (canBuyTurret()) {
            return buildTurret();
        } else {
            return doNothingCommand();
        }
    }

    /*
        Build Iron Curtain
    */
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

    /*
        Build Turret
    */
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

    /*
        my energy is equal or more than 20
    */
    private boolean canBuyEnergy() {
        return gameDetails.buildingsStats.get(ENERGY).price <= myself.energy;
    }
    /*
        my energy is equal or more than 30
    */
    private boolean canBuyTurret() {
        return gameDetails.buildingsStats.get(ATTACK).price <= myself.energy;
    }
    /*
        my energy is equal or more than 30
    */
    private boolean canBuyWall() {
        return gameDetails.buildingsStats.get(DEFENSE).price <= myself.energy;
    }

    /*
        Put wall on a lane with turret and no wall, on the 7th row
    */
    private String defendRow() {
        for (int i = 0; i < gameHeight; i++) {
            if (!enLaneInfo.get(i).get(0).isEmpty() && myLaneInfo.get(i).get(1).isEmpty()) {
                return DEFENSE.buildCommand(7,i);
            }
        }
        return buildTurret();
    }

    /* Do Nothing */
    private String doNothingCommand() {
        return NOTHING_COMMAND;
    }

    private <T> T getRandomElementOfList(List<T> list) {
        return list.get((new Random()).nextInt(list.size()));
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

        // Get all needed informations from lane y
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

        // Push all informations to list
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
}
