package flappybird;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.jme3.app.SimpleApplication;
import com.jme3.bounding.BoundingVolume;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.system.AppSettings;
import com.jme3.texture.Texture2D;
import com.jme3.ui.Picture;

public class RLGame extends SimpleApplication {
    
    private static final boolean LEARNING = true;
    public static final int LEARNING_TIME_STEPS = 6000;
    
    private static final float LOWER_PIPE_INITIAL_Y = -260;
    private static final float UPPER_PIPE_INITIAL_Y = 120;
    private static final float BIRD_INITIAL_Y = 100;
    private static final float BIRD_WIDTH = 30F;
    private static final float BIRD_HEIGHT = 30F;
    private static final int SCREEN_WIDTH = 200;
    private static final int PIPE_MOVE_STEP = 20;
    private static final int HALF_SCREEN_WIDTH = SCREEN_WIDTH / 2;
    private static final int SCREEN_HEIGHT = 300;
    private static final float PIPE_WIDTH = 25F;
    private static final float PIPE_HEIGHT = 300F;
    private static final int LEARNING_FRAME_RATE = 25;//100;
    private static final int PLAYING_FRAME_RATE = 3;
    private static final int FRAME_RATE = LEARNING ? LEARNING_FRAME_RATE : PLAYING_FRAME_RATE;
    
    public static final int SIGNAL_RESET_AND_CONTINUE = -1;
    public static final int SIGNAL_STAY = 0;
    public static final int SIGNAL_JUMP = 1;    
    private Learner learner;
    private int timeStep = 0;
    
    private static AppSettings settings;
    
    public static void main(String[] args){
        RLGame app = new RLGame();
        app.setShowSettings(false);
        settings = new AppSettings(true);
        settings.put("Width", SCREEN_WIDTH);
        settings.put("Height", SCREEN_HEIGHT);
        settings.put("Title", "Learn RL");
        settings.put("VSync", true);
        settings.put("Samples", 4); //Anti-Aliasing
        settings.setFrameRate(FRAME_RATE);
        app.setSettings(settings);
        app.setDisplayStatView(false);
        app.start();
    }

    private boolean dead = false;
    private Picture bird;
    private BitmapText scoreText;
    private int holeMode;
    private int score = 0;
    private final List<Picture> pipes = new ArrayList<>();
    
    private Picture getPicture(String name, float width, float height) {
        Picture pic = new Picture(name);
        Texture2D tex = (Texture2D) assetManager.loadTexture("Textures/"+name+".png");
        pic.setTexture(assetManager,tex,true);
        pic.setWidth(width);
        pic.setHeight(height);
        return pic;
    }    
    
    private final ActionListener actionListener = new ActionListener() {
        @Override
        public void onAction(String name, boolean keyPressed, float tpf) {
            if (name.equals("Jump") && !keyPressed) {
                jump();
            }
        }
    };
    
    private void jump() {
        float birdY = bird.getWorldTranslation().y;
        if (birdY < 240) {
            bird.move(0, 30, 0);
        } else {
            bird.move(0, 270 - birdY, 0); // put bird to the max height possible (SCREEN_HEIGHT - BIRD_HEIGHT)
        }
    }
    
    private int getBirdRandomY() {
        // returns 20, 30, ... 270
        
        Picture frontLowerPipe = pipes.get(0).getWorldTranslation().x < pipes.get(2).getWorldTranslation().x ?
                pipes.get(0) : pipes.get(2);

        int minY = (int) (0.1F * (frontLowerPipe.getWorldTranslation().y + PIPE_HEIGHT - 40));
        int maxY = (int) (0.1F * (frontLowerPipe.getWorldTranslation().y + PIPE_HEIGHT + 110));
        return 10 * ThreadLocalRandom.current().nextInt(Math.max(2,minY), Math.min(28,  maxY));
    }
    
    private int prevHoleMode = 0;
    private static final int MAX_HOLE_MODE_CHANGE = 1;
    private int getHoleMode() {
        // return a random number between 0 and 7 (inclusive)
        int newHoleMode = ThreadLocalRandom.current().nextInt(0, 8);
        if (newHoleMode > prevHoleMode) {
            if (newHoleMode > prevHoleMode + MAX_HOLE_MODE_CHANGE) {
                newHoleMode = prevHoleMode + MAX_HOLE_MODE_CHANGE;
            }
        } else {
            if (newHoleMode < prevHoleMode - MAX_HOLE_MODE_CHANGE) {
                newHoleMode = prevHoleMode - MAX_HOLE_MODE_CHANGE;
            }
        }
        prevHoleMode = newHoleMode;
        return newHoleMode;
    }
    @Override
    public void simpleInitApp() {
        flyCam.setEnabled(false); // disable default camera
        setDisplayFps(false);
        viewPort.setBackgroundColor(ColorRGBA.Cyan);
        for (int i = 0; i < 4; i++) {
            String imageName = i % 2 == 0 ? "lowerpipe" : "upperpipe";
            Picture pipe = getPicture(imageName, PIPE_WIDTH, PIPE_HEIGHT);
            pipes.add(pipe);
            rootNode.attachChild(pipe);
        }        

        bird = getPicture("bird", BIRD_WIDTH, BIRD_HEIGHT);
        rootNode.attachChild(bird);
        scoreText = new BitmapText(assetManager.loadFont("Interface/Fonts/Default.fnt"));
        scoreText.move(-2.5F, 4, 0);
        scoreText.setColor(ColorRGBA.Red);
        scoreText.setText("0");
        scoreText.setSize(0.5F);
        rootNode.attachChild(scoreText);
        
        inputManager.addMapping("Jump",  new KeyTrigger(KeyInput.KEY_UP));
        inputManager.addListener(actionListener, "Jump");
        reset();
        
        if (LEARNING) {
            Map<String, Float> Q = null;
            Path qPath = Paths.get("Q");
            if (Files.exists(qPath)) {
                // load previously learned Q values
                try (FileInputStream fin = new FileInputStream("Q");
                        ObjectInputStream ois = new ObjectInputStream(fin)) {
                    Q = (Map<String, Float>) ois.readObject();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            } else {
                Q = new HashMap<>();                
            }
            timeStep = 0;
            Path tPath = Paths.get("t");
            if (Files.exists(tPath)) {
                // load previous timestep
                try (FileInputStream fin = new FileInputStream("t");
                        ObjectInputStream ois = new ObjectInputStream(fin)) {
                    timeStep = (Integer) ois.readObject();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
            learner = new Learner(timeStep, Q);
        }
    }

    private void reset() {
        dead = false;
        score = 0;
        scoreText.setText("0");
        holeMode = getHoleMode();
        

        Picture pipe = pipes.get(0);
        pipe.move(HALF_SCREEN_WIDTH - pipe.getWorldTranslation().x,
                LOWER_PIPE_INITIAL_Y + 20 * holeMode - pipe.getWorldTranslation().y, 0);
        pipe = pipes.get(1);
        pipe.move(HALF_SCREEN_WIDTH - pipe.getWorldTranslation().x, 
                UPPER_PIPE_INITIAL_Y + 20 * holeMode - pipe.getWorldTranslation().y, 0);
        pipe = pipes.get(2);
        pipe.move(SCREEN_WIDTH - pipe.getWorldTranslation().x, 
                LOWER_PIPE_INITIAL_Y + 20 * holeMode - pipe.getWorldTranslation().y, 0);
        pipe = pipes.get(3);
        pipe.move(SCREEN_WIDTH - pipe.getWorldTranslation().x,
                UPPER_PIPE_INITIAL_Y + 20 * holeMode - pipe.getWorldTranslation().y, 0);
        
        bird.move(0, getBirdRandomY() - bird.getWorldTranslation().y, 0);
    }
    /* loop */
    @Override
    public void simpleUpdate(float tpf) {
        timeStep++;
        if (timeStep > LEARNING_TIME_STEPS) {
            settings.setFrameRate(PLAYING_FRAME_RATE);
        }
        //System.out.println("bird Y:" + bird.getWorldTranslation().y);
        if (LEARNING) {
            Picture frontLowerPipe = pipes.get(0).getWorldTranslation().x < pipes.get(2).getWorldTranslation().x ?
                    pipes.get(0) : pipes.get(2);
            int action = learner.update(dead, bird.getWorldTranslation().y, frontLowerPipe.getWorldTranslation().x, 
                    PIPE_HEIGHT + frontLowerPipe.getWorldTranslation().y, score);
            if (action == 1) {
                jump();
            }
        }
        
        if (dead) {
            reset();
            return;
        }
        for (Geometry pipe : pipes) {
            pipe.move(-PIPE_MOVE_STEP, 0, 0);
        }
        boolean firstPipe = false;
        for (Geometry pipe : pipes) {
            if (pipe.getWorldTranslation().x < -PIPE_WIDTH) {
                if (!firstPipe) {
                    firstPipe = true;
                    holeMode = getHoleMode();
                    scoreText.setText(String.valueOf(++score));
                }
                float y = pipe.getWorldTranslation().y;
                if (y < 0) { // lower pipe
                    pipe.move(SCREEN_WIDTH, -y + LOWER_PIPE_INITIAL_Y + 20 * holeMode, 0);
                } else {
                    pipe.move(SCREEN_WIDTH, -y + UPPER_PIPE_INITIAL_Y + 20 * holeMode, 0);
                }
            }
        }
        if (bird.getWorldTranslation().y > 15) {
            bird.move(0, -10, 0);  
        }
        boolean collisions = checkCollisions();
        if (collisions) {
            dead = true;
            scoreText.setText("G a m e   o v e r !!!");
        }
    }
    
    private boolean checkCollisions() {
        BoundingVolume birdVolume = bird.getWorldBound();
        Vector3f birdCenter = birdVolume.getCenter();
        float birdCenterX = birdCenter.x;
        float birdCenterY = birdCenter.y;
        float birdX1 = (float) (birdCenterX - 0.5 * BIRD_WIDTH);
        float birdY1 = (float) (birdCenterY - 0.5 * BIRD_HEIGHT);
        float birdX2 = (float) (birdCenterX + 0.5 * BIRD_WIDTH);
        float birdY2 = (float) (birdCenterY + 0.5 * BIRD_HEIGHT);
//        System.out.println("centerY of bird:" + birdCenter);
//        System.out.println("rect (" + birdX1 + "," + birdY1 + ") (" + birdX2 + "," + birdY2 + ")");
        for (Geometry pipe : pipes) {
            BoundingVolume pipeVolume = pipe.getWorldBound();
            Vector3f pipeCenter = pipeVolume.getCenter();
            float pipeCenterX = pipeCenter.x;
            float pipeCenterY = pipeCenter.y;
            float pipeX1 = (float) (pipeCenterX - 0.5 * PIPE_WIDTH);
            float pipeY1 = (float) (pipeCenterY - 0.5 * PIPE_HEIGHT);
            float pipeX2 = (float) (pipeCenterX + 0.5 * PIPE_WIDTH);
            float pipeY2 = (float) (pipeCenterY + 0.5 * PIPE_HEIGHT);
            if (((birdX1 < pipeX2 && birdX1 > pipeX1) || (birdX2 < pipeX2 && birdX2 > pipeX1))
                    && ((birdY1 < pipeY2 && birdY1 > pipeY1) || (birdY2 < pipeY2 && birdY2 > pipeY1))) {
                return true;
            }
        }
        return false;
    }
}