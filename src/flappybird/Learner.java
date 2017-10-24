package flappybird;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class Learner {
    private Map<String, Float> Q;
    private String prevStateAction;

    private static final float ALPHA = 0.7F;
    private static final float GAMMA = 1;

    PrintWriter log;
    private int timeStep = 0;

    {
        try {
            Path path = Paths.get("learning.txt");
            if (Files.exists(path)) {
                Files.delete(path);
            }
            log = new PrintWriter("learning.txt");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Learner(int timeStep, Map<String, Float> Q) {
        this.timeStep = timeStep;
        this.Q = Q;
    }

    /* updates the Q value of the previous state-action */
    public int update(boolean dead, float birdY, float pipeX, float pipeY, int score) {
        timeStep++;
        if (timeStep % 100 == 0) {
            System.out.println(timeStep + ", Q size:" + Q.size() + ", " + Q);
        }
        
        log.write("birdY:" + birdY + ", pipeX:" + pipeX + ", pipeY:" + pipeY + "\n");
        if (timeStep == RLGame.LEARNING_TIME_STEPS) {
            serializeObjects();
        }
        if (timeStep < RLGame.LEARNING_TIME_STEPS) {
            int retVal = learn(dead, birdY, pipeX, pipeY, score);
            log.write(timeStep + ", Q size:" + Q.size() + ", " + Q + "\n");
            
            return retVal;
        } else {
            return play(dead, birdY, pipeX, pipeY, score);
        }
    }
    
    private void serializeObjects() {
        if (timeStep == RLGame.LEARNING_TIME_STEPS) {
            try {
                Path path = Paths.get("Q");
                if (Files.exists(path)) {
                    Files.delete(path);
                }
                Files.createFile(path);
                
                path = Paths.get("t");
                if (Files.exists(path)) {
                    Files.delete(path);
                }
                Files.createFile(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            try (FileOutputStream fos = new FileOutputStream("Q");
                ObjectOutputStream ous = new ObjectOutputStream(fos)) {
                ous.writeObject(Q);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try (FileOutputStream fos = new FileOutputStream("t");
                    ObjectOutputStream ous = new ObjectOutputStream(fos)) {
                    ous.writeObject(Integer.valueOf(timeStep));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    private int learn(boolean dead, float birdY, float pipeX, float pipeY,
            int score) {
        int yDistance = (int) (birdY - pipeY);
        String state = yDistance + "-" + (int) pipeX;
        if (prevStateAction == null) {
            // first ever state (beginning of training)
            int action = getRandomAction();
            prevStateAction = state + "-" + action;
            return action;
        } else {
            // take the action whose value is larger 99% of the time
            int action = getExploreExploitAction(state); 
            int reward = dead ? -1000 : 1;
            float oldValue = getQ(prevStateAction);
            float newValue = (1 - ALPHA) * oldValue
                    + ALPHA * (reward + GAMMA * getMaxQ(state));
            Q.put(prevStateAction, newValue);
            prevStateAction = state + "-" + action;
            if (dead) {
                return RLGame.SIGNAL_RESET_AND_CONTINUE;
            } else {
                return action;
            }
        }
    }

    private int play(boolean dead, float birdY, float pipeX, float pipeY,
            int score) {
        int yDistance = (int) (birdY - pipeY);
        String state = yDistance + "-" + (int) pipeX;
        float stateAction0Value = getQ(state, 0);
        float stateAction1Value = getQ(state, 1);
        System.out.println("state:" + state + ", 0val:" + stateAction0Value
                + ", 1val:" + stateAction1Value + ", score:" + score);
        return (stateAction0Value > stateAction1Value) ? 0 : 1;
    }

    private int getRandomAction() {
        // returns 0 or 1 randomly
        return ThreadLocalRandom.current().nextInt(0, 2);
    }
    
    private int getExploreExploitAction(String state) {
        // returns 0 or 1 randomly
        // exploit 99% of the time
        float action0Value = getQ(state, 0);
        float action1Value = getQ(state, 1);
        if (timeStep % 100 == 0) {
            // explore, return the smaller value
            return action0Value < action1Value ? 0 : 1;
        } else {
            // exploit, return the bigger value
            return action0Value > action1Value ? 0 : 1;
        }
    }

    private float getMaxQ(String state) {
        // return the maximum of Q(state, 0) and Q(state, 1)
        return Math.max(getQ(state, 0), getQ(state, 1));
    }
    
    private float getQ(String state, int action) {
        return getQ(state + "-" + action);
    }

    private float getQ(String stateAction) {
        Float value = Q.get(stateAction);
        return (value == null ? 0 : value);
    }
}
