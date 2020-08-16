package remix;

import processing.core.PApplet;
import processing.core.PFont;
import processing.event.KeyEvent;
import processing.event.MouseEvent;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Index for Intellij
 * Classes: {@link Cell} {@link Codon} {@link CodonInfo}
 * {@link Genome} {@link Particle}
 * Main processing classes:
 * {@link #setup()} {@link #draw()} {@link #settings()}
 */
public class Virus extends PApplet {
    int WORLD_SIZE = 12;
    int W_W = 1728;
    int W_H = 972;
    int foodLimit = 180;
    Cell[][] cells = new Cell[WORLD_SIZE][WORLD_SIZE];
    CopyOnWriteArrayList<Particle> foods = new CopyOnWriteArrayList<>();
    CopyOnWriteArrayList<Particle> wastes = new CopyOnWriteArrayList<>();
    CopyOnWriteArrayList<Particle> ugo = new CopyOnWriteArrayList<>();
    @SuppressWarnings("unchecked")
    Collection<Particle>[] particles = (Collection<Particle>[]) new Collection[]{foods, wastes, ugo};
    float BIG_FACTOR = 100;
    float PLAY_SPEED = 0.6f;
    double GENE_TICK_TIME = 40.0f;
    double margin = 4;
    int START_LIVING_COUNT = 0;
    int[] cellCounts = {0,0,0};

    double REMOVE_WASTE_SPEED_MULTI = 0.001f;
    double removeWasteTimer = 1.0f;

    double GENE_TICK_ENERGY = 0.014f;
    double WALL_DAMAGE = 0.01f;
    double CODON_DEGRADE_SPEED = 0.008f;
    double EPS = 0.00000001f;

    String starterGenome = "46-11-22-33-11-22-33-45-44-57__-67__";
    boolean canDrag = false;
    double clickWorldX = -1;
    double clickWorldY = -1;
    boolean DQclick = false;
    int[] codonToEdit = {-1,-1,0,0};
    double[] genomeListDims = {70,430,360,450};
    double[] editListDims = {550,430,180,450};
    double[] arrowToDraw = null;
    Cell selectedCell = null;
    Cell UGOcell;
    int lastEditTimeStamp = 0;
    int handColor = color(0,128,0);
    int TELOMERE_COLOR = color(0,0,0);
    int WASTE_COLOR = color(100,65,0);
    int MAX_CODON_COUNT = 20; // If a cell were to have more codons in its DNA than this number if it were to absorb a cirus particle, it won't absorb it.

    double SPEED_LOW = 0.01f;
    double SPEED_HIGH = 0.02f;
    double MIN_ARROW_LENGTH_TO_PRODUCE = 0.4f;

    double ZOOM_THRESHOLD = 0;//80;
    PFont font;
    public void setup(){
        getSurface().setTitle("VirusGameHQ");
        font = loadFont("Jygquip1-96.vlw");
        /*for(int j = 0; j < 3; j++){
            ArrayList<Particle> newList = new ArrayList<>(0);
            particles.add(newList);
        }*/
        for(int y = 0; y < WORLD_SIZE; y++){
            for(int x = 0; x < WORLD_SIZE; x++){
                int t = getTypeFromXY(x,y);
                cells[y][x] = new Cell(x,y,t,0,1,starterGenome);
                if(t == 2){
                    START_LIVING_COUNT++;
                    cellCounts[0]++;
                }
            }
        }


        UGOcell = new Cell(-1,-1,2,0,1,"00-00-00-00-00");

        Thread tickThread = new Thread("TickThread") {
            @Override
            public void run() {
                tickAsync();
            }
        };
        tickThread.setDaemon(true);
        tickThread.start();
    }
    public int getTypeFromXY(int preX, int preY){
        int[] weirdo = {0,1,1,2};
        int x = (preX/4)*3;
        x += weirdo[preX%4];
        int y = (preY/4)*3;
        y += weirdo[preY%4];
        int result = 0;
        for(int i = 1; i < WORLD_SIZE; i *= 3){
            if((x/i)%3 == 1 && (y/i)%3 == 1){
                result = 1;
                int xPart = x%i;
                int yPart = y%i;
                boolean left = (xPart == 0);
                boolean right = (xPart == i-1);
                boolean top = (yPart == 0);
                boolean bottom = (yPart == i-1);
                if(left || right || top || bottom){
                    result = 2;
                }
            }
        }
        return result;
    }

    boolean wasMouseDown = false;
    double camX = 0;
    double camY = 0;
    double MIN_CAM_S = ((float)W_H)/WORLD_SIZE;
    double camS = MIN_CAM_S;
    double camSReal = camS;
    public int tickCount;
    //final Object waitLock = new Object();
    final Object waitLock2 = new Object();
    public void draw(){
        // doParticleCountControl();
        // iterate();
        detectInput();
        drawBackground();
        //synchronized (waitLock) {
            drawCells();
            drawParticles();
            drawExtras();
            synchronized (waitLock2) {
                waitLock2.notify();
            }
        //}
        drawUI();
    }
    public void tickAsync(){
        Thread currentThread = Thread.currentThread();
        try {
            while (!currentThread.isInterrupted()) {
                synchronized (waitLock2) {
                    waitLock2.wait(18L);
                }
                //synchronized (waitLock) {
                    doParticleCountControl();
                    iterate();
                    tickCount++;
                //}
            }
        } catch (InterruptedException ignored) {}

    }
    public void drawExtras(){
        if(arrowToDraw != null){
            if(euclidLength(arrowToDraw) > MIN_ARROW_LENGTH_TO_PRODUCE){
                stroke(0);
            }else{
                stroke(0,0,0,80);
            }
            drawArrow(arrowToDraw[0],arrowToDraw[1],arrowToDraw[2],arrowToDraw[3]);
        }
    }
    public void doParticleCountControl(){
        // ArrayList<Particle> foods = particles.get(0);
        while(foods.size() < foodLimit){
            int choiceX = -1;
            int choiceY = -1;
            while(choiceX == -1 || cells[choiceY][choiceX].type >= 1){
                choiceX = (int)random(0,WORLD_SIZE);
                choiceY = (int)random(0,WORLD_SIZE);
            }
            double extraX = random(0.3f,0.7f);
            double extraY = random(0.3f,0.7f);
            double x = choiceX+extraX;
            double y = choiceY+extraY;
            double[] coor = {x,y};
            Particle newFood = new Particle(coor,0,tickCount);
            foods.add(newFood);
            newFood.addToCellList();
        }

        // ArrayList<Particle> wastes = particles.get(1);
        if(wastes.size() > foodLimit){
            removeWasteTimer -= (wastes.size()-foodLimit)*REMOVE_WASTE_SPEED_MULTI;
            if(removeWasteTimer < 0){
                int choiceIndex = -1;
                while((choiceIndex == -1 || getCellAt(wastes.get(choiceIndex).coor,true).type == 2)){
                    choiceIndex = (int)(Math.random()*wastes.size());
                } // If possible, choose a particle that is NOT in a cell at the moment.
                wastes.get(choiceIndex).removeParticle();
                removeWasteTimer++;
            }
        }
    }
    public double[] getRandomVelo(){
        double sp = Math.random()*(SPEED_HIGH-SPEED_LOW)+SPEED_LOW;
        double ang = random(0,2*PI);
        double vx = sp*Math.cos(ang);
        double vy = sp*Math.sin(ang);
        return new double[]{vx, vy};
    }
    public void iterate(){
        for(int z = 0; z < 3; z++){
            Collection<Particle> sparticles = particles[z];
            for (Particle p : sparticles) {
                p.iterate();
            }
        }
        for(int y = 0; y < WORLD_SIZE; y++){
            for(int x = 0; x < WORLD_SIZE; x++){
                cells[y][x].iterate();
            }
        }
    }
    public void drawParticles(){
        for(int z = 0; z < 3; z++){
            Collection<Particle> sparticles = particles[z];
            for (Particle p : sparticles) {
                p.drawParticle(trueXtoAppX(p.coor[0]), trueYtoAppY(p.coor[1]), trueStoAppS(1));
            }
        }
    }
    public void checkGLclick(){
        double gx = genomeListDims[0];
        double gy = genomeListDims[1];
        double gw = genomeListDims[2];
        double gh = genomeListDims[3];
        double rMouseX = ((mouseX-W_H)-gx)/gw;
        double rMouseY = (mouseY-gy)/gh;
        if(rMouseX >= 0 && rMouseX < 1 && rMouseY >= 0){
            if(rMouseY < 1){
                codonToEdit[0] = (int)(rMouseX*2);
                codonToEdit[1] = (int)(rMouseY*selectedCell.genome.codons.size());
            }else if(selectedCell == UGOcell){
                String genomeString;
                if(rMouseX < 0.5f){
                    genomeString = UGOcell.genome.getGenomeStringShortened();
                }else{
                    genomeString = UGOcell.genome.getGenomeStringLengthened();
                }
                selectedCell = UGOcell = new Cell(-1,-1,2,0,1,genomeString);
            }
        }
    }
    public void checkETclick(){
        double ex = editListDims[0];
        double ey = editListDims[1];
        double ew = editListDims[2];
        double eh = editListDims[3];
        double rMouseX = ((mouseX-W_H)-ex)/ew;
        double rMouseY = (mouseY-ey)/eh;
        if(rMouseX >= 0 && rMouseX < 1 && rMouseY >= 0 && rMouseY < 1){
            int optionCount = CodonInfo.getOptionSize(codonToEdit[0]);
            int choice = (int)(rMouseY*optionCount);
            if(codonToEdit[0] == 1 && choice >= optionCount-2){
                int diff = 1;
                if(rMouseX < 0.5f){
                    diff = -1;
                }
                if(choice == optionCount-2){
                    codonToEdit[2] = loopCodonInfo(codonToEdit[2]+diff);
                }else{
                    codonToEdit[3] = loopCodonInfo(codonToEdit[3]+diff);
                }
            }else{
                Codon thisCodon = selectedCell.genome.codons.get(codonToEdit[1]);
                if(codonToEdit[0] == 1 && choice == 7){
                    if(thisCodon.codonInfo[1] != 7 ||
                            thisCodon.codonInfo[2] != codonToEdit[2] || thisCodon.codonInfo[3] != codonToEdit[3]){
                        thisCodon.setInfo(1,choice);
                        thisCodon.setInfo(2,codonToEdit[2]);
                        thisCodon.setInfo(3,codonToEdit[3]);
                        if(selectedCell != UGOcell){
                            lastEditTimeStamp = tickCount;
                            selectedCell.tamper();
                        }
                    }
                }else{
                    if(thisCodon.codonInfo[codonToEdit[0]] != choice){
                        thisCodon.setInfo(codonToEdit[0],choice);
                        if(selectedCell != UGOcell){
                            lastEditTimeStamp = tickCount;
                            selectedCell.tamper();
                        }
                    }
                }
            }
        }else{
            codonToEdit[0] = codonToEdit[1] = -1;
        }
    }
    public int loopCodonInfo(int val){
        while(val < -30){
            val += 61;
        }
        while(val > 30){
            val -= 61;
        }
        return val;
    }
    public int codonCharToVal(char c){
        int val = (int)(c) - (int)('A');
        return val-30;
    }
    public String codonValToChar(int i){
        int val = (i+30) + (int)('A');
        return (char)val+"";
    }
    boolean up;
    boolean down;
    boolean left;
    boolean right;
    public void detectInput(){
        if (mousePressed){
            arrowToDraw = null;
            if(!wasMouseDown) {
                if(mouseX < W_H){
                    codonToEdit[0] = codonToEdit[1] = -1;
                    clickWorldX = appXtoTrueX(mouseX);
                    clickWorldY = appYtoTrueY(mouseY);
                    canDrag = true;
                }else{
                    if(selectedCell != null){
                        if(codonToEdit[0] >= 0){
                            checkETclick();
                        }
                        checkGLclick();
                    }
                    if(selectedCell == UGOcell){
                        if((mouseX >= W_H+530 && codonToEdit[0] == -1) || mouseY < 160){
                            selectedCell = null;
                        }
                    }else if(mouseX > W_W-160 && mouseY < 160){
                        selectedCell = UGOcell;
                    }
                    canDrag = false;
                }
                DQclick = false;
            }else if(canDrag){
                double newCX = appXtoTrueX(mouseX);
                double newCY = appYtoTrueY(mouseY);
                if(newCX != clickWorldX || newCY != clickWorldY){
                    DQclick = true;
                }
                if(selectedCell == UGOcell){
                    stroke(0,0,0);
                    arrowToDraw = new double[]{clickWorldX,clickWorldY,newCX,newCY};
                }else{
                    camX -= (newCX-clickWorldX);
                    camY -= (newCY-clickWorldY);
                }
            }
        } else {
            if(wasMouseDown){
                if(selectedCell == UGOcell && arrowToDraw != null){
                    if(euclidLength(arrowToDraw) > MIN_ARROW_LENGTH_TO_PRODUCE){
                        //synchronized (waitLock) {
                            produceUGO(arrowToDraw);
                        //}
                    }
                }
                if(!DQclick && canDrag){
                    double[] mCoor = {clickWorldX,clickWorldY};
                    Cell clickedCell = getCellAt(mCoor,false);
                    if(selectedCell != UGOcell){
                        selectedCell = null;
                    }
                    if(clickedCell != null && clickedCell.type == 2){
                        selectedCell = clickedCell;
                    }
                }
            }
            clickWorldX = -1;
            clickWorldY = -1;
            arrowToDraw = null;
        }
        wasMouseDown = mousePressed;
        if (camS != camSReal) {
            double camSNew;
            if ((Math.abs(camS - camSReal) * 500) / (camS + camSReal) < 1) {
                camSNew = camSReal;
            } else {
                camSNew = (camS + camSReal) / 2;
            }
            zoomTo(camSNew);
        }
        if (left != right) {
            if (right) {
                camX += (7F/camS);
            } else {
                camX -= (7F/camS);
            }
        }
        if (down != up) {
            if (down) {
                camY += (7F/camS);
            } else {
                camY -= (7F/camS);
            }
        }
    }
    public void zoomTo(double camSNew) {
        double thisZoomF = camSNew/camS;
        double worldX = mouseX/camS+camX;
        double worldY = mouseY/camS+camY;
        camX = (camX-worldX)/thisZoomF+worldX;
        camY = (camY-worldY)/thisZoomF+worldY;
        // camS *= thisZoomF;
        camS = camSNew;
    }
    public void mouseWheel(MouseEvent event) {
        double ZOOM_F = 1.05f;
        double thisZoomF;
        float e = event.getCount();
        if(e == 1){
            thisZoomF = 1/ZOOM_F;
        }else{
            thisZoomF = ZOOM_F;
        }
        /*double worldX = mouseX/camS+camX;
        double worldY = mouseY/camS+camY;
        camX = (camX-worldX)/thisZoomF+worldX;
        camY = (camY-worldY)/thisZoomF+worldY;*/
        camSReal *= thisZoomF;
    }
    public double euclidLength(double[] coor){
        return Math.sqrt(Math.pow(coor[0]-coor[2],2)+Math.pow(coor[1]-coor[3],2));
    }
    public void produceUGO(double[] coor){
        if(getCellAt(coor,false) != null && getCellAt(coor,false).type == 0){
            String genomeString = UGOcell.genome.getGenomeString();
            Particle newUGO = new Particle(coor,2,genomeString,tickCount);
            ugo.add(newUGO);
            newUGO.addToCellList();
            lastEditTimeStamp = tickCount;
        }
    }
    public void drawBackground(){
        background(255);
    }
    public void drawArrow(double dx1, double dx2, double dy1, double dy2){
        float x1 = (float)trueXtoAppX(dx1);
        float y1 = (float)trueYtoAppY(dx2);
        float x2 = (float)trueXtoAppX(dy1);
        float y2 = (float)trueYtoAppY(dy2);
        strokeWeight((float)(0.03f*camS));
        line(x1,y1,x2,y2);
        float angle = atan2(y2-y1,x2-x1);
        float head_size = (float)(0.2f*camS);
        float x3 = x2+head_size*cos(angle+PI*0.8f);
        float y3 = y2+head_size*sin(angle+PI*0.8f);
        line(x2,y2,x3,y3);
        float x4 = x2+head_size*cos(angle-PI*0.8f);
        float y4 = y2+head_size*sin(angle-PI*0.8f);
        line(x2,y2,x4,y4);
    }
    public String framesToTime(double f){
        double ticks = f/GENE_TICK_TIME;
        String timeStr = nf((float)ticks,0,1);
        if(ticks >= 1000){
            timeStr = (int)(Math.round(ticks))+"";
        }
        return timeStr+"t since";
    }
    public String count(int count, String s){
        if(count == 1){
            return count+" "+s;
        }else{
            return count+" "+s+"s";
        }
    }
    public void drawUI(){
        pushMatrix();
        translate(W_H,0);
        fill(0);
        noStroke();
        rect(0,0,W_W-W_H,W_H);
        fill(255);
        textFont(font,48);
        textAlign(LEFT);
        text(framesToTime(tickCount)+" start",25,60);
        text(framesToTime(tickCount-lastEditTimeStamp)+" edit",25,108);
        textFont(font,36);
        text("Healthy: "+cellCounts[0]+" / "+START_LIVING_COUNT,360,50);
        text("Tampered: "+cellCounts[1]+" / "+START_LIVING_COUNT,360,90);
        text("Dead: "+cellCounts[2]+" / "+START_LIVING_COUNT,360,130);
        if(selectedCell != null){
            drawCellStats();
        }
        popMatrix();
        drawUGObutton((selectedCell != UGOcell));
    }
    public void drawUGObutton(boolean drawUGO){
        fill(80);
        noStroke();
        rect(W_W-130,10,120,140);
        fill(255);
        textAlign(CENTER);
        if(drawUGO){
            textFont(font,48);
            text("MAKE",W_W-70,70);
            text("UGO",W_W-70,120);
        }else{
            textFont(font,36);
            text("CANCEL",W_W-70,95);
        }
    }
    public void drawCellStats(){
        boolean isUGO = (selectedCell.x == -1);
        fill(80);
        noStroke();
        rect(10,160,530,W_H-170);
        if(!isUGO){
            rect(540,160,200,270);
        }
        fill(255);
        textFont(font,96);
        textAlign(LEFT);
        text(selectedCell.getCellName(),25,255);
        if(!isUGO){
            textFont(font,32);
            text("Inside this cell,",555,200);
            text("there are:",555,232);
            text(count(selectedCell.getParticleCount(-1),"particle"),555,296);
            text("("+count(selectedCell.getParticleCount(0),"food")+")",555,328);
            text("("+count(selectedCell.getParticleCount(1),"waste")+")",555,360);
            text("("+count(selectedCell.getParticleCount(2),"UGO")+")",555,392);
            drawBar(color(255,255,0),selectedCell.energy,"Energy",290);
            drawBar(color(210,50,210),selectedCell.wallHealth,"Wall health",360);
        }
        drawGenomeAsList(selectedCell.genome,genomeListDims);
        drawEditTable(editListDims);
        if(!isUGO){
            textFont(font,32);
            textAlign(LEFT);
            text("Memory: "+getMemory(selectedCell),25,940);
        }
    }
    public String getMemory(Cell c){
        if(c.memory.length() == 0){
            return "[NOTHING]";
        }else{
            return "\""+c.memory+"\"";
        }
    }
    public void drawGenomeAsList(Genome g, double[] dims){
        double x = dims[0];
        double y = dims[1];
        double w = dims[2];
        double h = dims[3];
        int GENOME_LENGTH = g.codons.size();
        double appCodonHeight = h/GENOME_LENGTH;
        double appW = w*0.5f-margin;
        textFont(font,30);
        textAlign(CENTER);
        pushMatrix();
        dTranslate(x,y);
        pushMatrix();
        dTranslate(0,appCodonHeight*(g.appRO+0.5f));
        if(selectedCell != UGOcell){
            drawGenomeArrows(w,appCodonHeight);
        }
        popMatrix();
        for(int i = 0; i < GENOME_LENGTH; i++){
            double appY = appCodonHeight*i;
            Codon codon = g.codons.get(i);
            for(int p = 0; p < 2; p++){
                double extraX = (w*0.5f-margin)*p;
                int fillColor = codon.getColor(p);
                int textColor = codon.getTextColor(p);
                fill(0);
                dRect(extraX+margin,appY+margin,appW,appCodonHeight-margin*2);
                if(codon.hasSubstance()){
                    fill(fillColor);
                    double trueW = appW*codon.codonHealth;
                    double trueX = extraX+margin;
                    if(p == 0){
                        trueX += appW*(1-codon.codonHealth);
                    }
                    dRect(trueX,appY+margin,trueW,appCodonHeight-margin*2);
                }
                fill(textColor);
                dText(codon.getText(p),extraX+w*0.25f,appY+appCodonHeight/2+11);

                if(p == codonToEdit[0] && i == codonToEdit[1]){
                    double highlightFac = 0.5f+0.5f*sin(tickCount*0.25f);
                    fill(255,255,255,(float)(highlightFac*140));
                    dRect(extraX+margin,appY+margin,appW,appCodonHeight-margin*2);
                }
            }
        }
        if(selectedCell == UGOcell){
            fill(255);
            textFont(font,60);
            double avgY = (h+height-y)/2;
            dText("( - )",w*0.25f,avgY+11);
            dText("( + )",w*0.75f-margin,avgY+11);
        }
        popMatrix();
    }
    public void drawEditTable(double[] dims){
        double x = dims[0];
        double y = dims[1];
        double w = dims[2];
        double h = dims[3];

        double appW = w-margin*2;
        textFont(font,30);
        textAlign(CENTER);

        int p = codonToEdit[0];
        int s = codonToEdit[2];
        int e = codonToEdit[3];
        if(p >= 0){
            pushMatrix();
            dTranslate(x,y);
            int choiceCount = CodonInfo.getOptionSize(codonToEdit[0]);
            double appChoiceHeight = h/choiceCount;
            for(int i = 0; i < choiceCount; i++){
                double appY = appChoiceHeight*i;
                int fillColor = intToColor(CodonInfo.getColor(p,i));
                int textColor = intToColor(CodonInfo.getTextColor(p,i));
                fill(fillColor);
                dRect(margin,appY+margin,appW,appChoiceHeight-margin*2);
                fill(textColor);
                dText(CodonInfo.getTextSimple(p, i, s, e),w*0.5f,appY+appChoiceHeight/2+11);
            }
            popMatrix();
        }
    }
    public int colorInterp(int a, int b, double x){ // Unused
        float newR = (float)(red(a)+(red(b)-red(a))*x);
        float newG = (float)(green(a)+(green(b)-green(a))*x);
        float newB = (float)(blue(a)+(blue(b)-blue(a))*x);
        return color(newR, newG, newB);
    }
    public void drawGenomeArrows(double dw, double dh){
        float w = (float)dw;
        float h = (float)dh;
        fill(255);
        beginShape();
        vertex(-5,0);
        vertex(-45,-40);
        vertex(-45,40);
        endShape(CLOSE);
        beginShape();
        vertex(w+5,0);
        vertex(w+45,-40);
        vertex(w+45,40);
        endShape(CLOSE);
        noStroke();
        rect(0,-h/2,w,h);
    }
    public void dRect(double x, double y, double w, double h){
        noStroke();
        rect((float)x, (float)y, (float)w, (float)h);
    }
    public void dText(String s, double x, double y){
        text(s, (float)x, (float)y);
    }
    public void dTranslate(double x, double y){
        translate((float)x, (float)y);
    }
    public void daLine(double[] a, double[] b){
        float x1 = (float)trueXtoAppX(a[0]);
        float y1 = (float)trueYtoAppY(a[1]);
        float x2 = (float)trueXtoAppX(b[0]);
        float y2 = (float)trueYtoAppY(b[1]);
        strokeWeight((float)(0.03f*camS));
        line(x1,y1,x2,y2);
    }
    public void drawBar(int col, double stat, String s, double y){
        fill(150);
        rect(25,(float)y,500,60);
        fill(col);
        rect(25,(float)y,(float)(stat*500),60);
        fill(0);
        textFont(font,48);
        textAlign(LEFT);
        text(s+": "+nf((float)(stat*100),0,1)+"%",35,(float)y+47);
    }
    public void drawCells(){
        for(int y = 0; y < WORLD_SIZE; y++){
            for(int x = 0; x < WORLD_SIZE; x++){
                cells[y][x].drawCell(trueXtoAppX(x),trueYtoAppY(y),trueStoAppS(1));
            }
        }
    }
    public double trueXtoAppX(double x){
        return (x-camX)*camS;
    }
    public double trueYtoAppY(double y){
        return (y-camY)*camS;
    }
    public double appXtoTrueX(double x){
        return x/camS+camX;
    }
    public double appYtoTrueY(double y){
        return y/camS+camY;
    }
    public double trueStoAppS(double s){
        return s*camS;
    }
    public int getCellTypeAt(double x, double y, boolean allowLoop){
        int ix = (int)x;
        int iy = (int)y;
        if(allowLoop){
            ix = (ix+WORLD_SIZE)%WORLD_SIZE;
            iy = (iy+WORLD_SIZE)%WORLD_SIZE;
        }else{
            if(ix < 0 || ix >= WORLD_SIZE || iy < 0 || iy >= WORLD_SIZE){
                return 0;
            }
        }
        return cells[iy][ix].type;
    }
    public int getCellTypeAt(double[] coor, boolean allowLoop){
        return getCellTypeAt(coor[0],coor[1],allowLoop);
    }
    public Cell getCellAt(double x, double y, boolean allowLoop){
        int ix = (int)x;
        int iy = (int)y;
        if(allowLoop){
            ix = (ix+WORLD_SIZE)%WORLD_SIZE;
            iy = (iy+WORLD_SIZE)%WORLD_SIZE;
        }else{
            if(ix < 0 || ix >= WORLD_SIZE || iy < 0 || iy >= WORLD_SIZE){
                return null;
            }
        }
        return cells[iy][ix];
    }
    public Cell getCellAt(double[] coor, boolean allowLoop){
        return getCellAt(coor[0],coor[1],allowLoop);
    }
    public boolean cellTransfer(double x1, double y1, double x2, double y2){
        int ix1 = (int)Math.floor(x1);
        int iy1 = (int)Math.floor(y1);
        int ix2 = (int)Math.floor(x2);
        int iy2 = (int)Math.floor(y2);
        return (ix1 != ix2 || iy1 != iy2);
    }
    public boolean cellTransfer(double[] coor1, double[] coor2){
        return cellTransfer(coor1[0], coor1[1], coor2[0], coor2[1]);
    }
    public double loopIt(double x, double len, boolean evenSplit){
        if(evenSplit){
            while(x >= len*0.5f){
                x -= len;
            }
            while(x < -len*0.5f){
                x += len;
            }
        }else{
            while(x > len-0.5f){
                x -= len;
            }
            while(x < -0.5f){
                x += len;
            }
        }
        return x;
    }
    public int loopItInt(int x, int len){
        return (x+len*10)%len;
    }
    public int intToColor(int[] c){
        return color(c[0],c[1],c[2]);
    }
    public int transperize(int col, double trans){
        float alpha = (float)(trans*255);
        return color(red(col),green(col),blue(col),alpha);
    }
    public String infoToString(int[] info){
        String result = info[0]+""+info[1];
        if(info[1] == 7){
            result += codonValToChar(info[2])+""+codonValToChar(info[3]);
        }
        return result;
    }
    public int[] stringToInfo(String str){
        int[] info = new int[4];
        for(int i = 0; i < 2; i++){
            info[i] = Integer.parseInt(str.substring(i,i+1));
        }
        if(info[1] == 7){
            for(int i = 2; i < 4; i++){
                char c = str.charAt(i);
                info[i] = codonCharToVal(c);
            }
        }
        return info;
    }
    class Cell{
        int x;
        int y;
        int type;
        double wallHealth;
        Genome genome;
        double geneTimer;
        double energy;
        double E_RECIPROCAL = 0.3678794411f;
        boolean tampered = false;
        ArrayList<ArrayList<Particle>> particlesInCell = new ArrayList<>(0);

        ArrayList<double[]> laserCoor = new ArrayList<>();
        Particle laserTarget = null;
        int laserT = -9999;
        int LASER_LINGER_TIME = 30;
        String memory = "";
        /*
        0: empty
        1: empty, inaccessible
        2: normal cell
        3: waste management cell
        4: gene-removing cell
        */
        int dire;
        public Cell(int ex, int ey, int et, int ed, double ewh, String eg){
            for(int j = 0; j < 3; j++){
                ArrayList<Particle> newList = new ArrayList<>(0);
                particlesInCell.add(newList);
            }
            x = ex;
            y = ey;
            type = et;
            dire = ed;
            wallHealth = ewh;
            genome = new Genome(eg,false);
            genome.rotateOn = (int)(Math.random()*genome.codons.size());
            geneTimer = Math.random()*GENE_TICK_TIME;
            energy = 0.5f;
        }
        public void drawCell(double x, double y, double s){
            pushMatrix();
            translate((float)x,(float)y);
            scale((float)(s/BIG_FACTOR));
            noStroke();
            if(type == 1){
                fill(60.01F,60,60);
                rectSmooth(0,0,BIG_FACTOR,BIG_FACTOR);
            }else if(type == 2){
                if(this == selectedCell){
                    fill(0,255,255);
                }else if(tampered){
                    fill(205,225,70);
                }else{
                    fill(225,190,225);
                }
                rect(0,0,BIG_FACTOR,BIG_FACTOR);
                fill(170,100,170);
                float w = (float)(BIG_FACTOR*0.08f*wallHealth);
                rectSmooth(0,0,BIG_FACTOR,w);
                rectSmooth(0,BIG_FACTOR-w,BIG_FACTOR,w);
                rectSmooth(0,0,w,BIG_FACTOR);
                rectSmooth(BIG_FACTOR-w,0,w,BIG_FACTOR);

                pushMatrix();
                translate(BIG_FACTOR*0.5f,BIG_FACTOR*0.5f);
                stroke(0);
                strokeWeight(1);
                drawInterpreter();
                drawEnergy();
                genome.drawCodons();
                genome.drawHand();
                popMatrix();
            }
            popMatrix();
            if(type == 2){
                drawLaser();
            }
        }
        public void drawInterpreter(){
            int GENOME_LENGTH = genome.codons.size();
            double CODON_ANGLE = (double)(1.0f)/GENOME_LENGTH*2*PI;
            double INTERPRETER_SIZE = 23;
            double col = 1;
            double gtf = geneTimer/GENE_TICK_TIME;
            if(gtf < 0.5f){
                col = Math.min(1,(0.5f-gtf)*4);
            }
            pushMatrix();
            rotate((float)(-PI/2+CODON_ANGLE*genome.appRO));
            fill((float)(col*255));
            beginShape();
            strokeWeight(BIG_FACTOR*0.01f);
            stroke(80);
            vertex(0,0);
            vertex((float)(INTERPRETER_SIZE*Math.cos(CODON_ANGLE*0.5f)),(float)(INTERPRETER_SIZE*Math.sin(CODON_ANGLE*0.5f)));
            vertex((float)(INTERPRETER_SIZE*Math.cos(-CODON_ANGLE*0.5f)),(float)(INTERPRETER_SIZE*Math.sin(-CODON_ANGLE*0.5f)));
            endShape(CLOSE);
            popMatrix();
        }
        public void drawLaser(){
            if(tickCount < laserT+LASER_LINGER_TIME){
                double alpha = (double)((laserT+LASER_LINGER_TIME)-tickCount)/LASER_LINGER_TIME;
                stroke(transperize(handColor,alpha));
                strokeWeight(0.033333f*BIG_FACTOR);
                double[] handCoor = getHandCoor();
                if(laserTarget == null){
                    for(double[] singleLaserCoor : laserCoor){
                        daLine(handCoor,singleLaserCoor);
                    }
                }else{
                    double[] targetCoor = laserTarget.coor;
                    daLine(handCoor,targetCoor);
                }
            }
        }
        public void drawEnergy(){
            noStroke();
            fill(0,0,0);
            ellipse(0,0,17,17);
            fill(255,255,0);
            pushMatrix();
            scale((float)Math.sqrt(energy));
            drawLightning();
            popMatrix();
        }
        public void drawLightning(){
            pushMatrix();
            scale(1.2f);
            noStroke();
            beginShape();
            vertex(-1,-7);
            vertex(2,-7);
            vertex(0,-3);
            vertex(2.5f,-3);
            vertex(0.5f,1);
            vertex(3,1);
            vertex(-1.5f,7);
            vertex(-0.5f,3);
            vertex(-3,3);
            vertex(-1,-1);
            vertex(-4,-1);
            endShape(CLOSE);
            popMatrix();
        }
        public void iterate(){
            if(type == 2){
                if(energy > 0){
                    double oldGT = geneTimer;
                    geneTimer -= PLAY_SPEED;
                    if(geneTimer <= GENE_TICK_TIME/2.0f && oldGT > GENE_TICK_TIME/2.0f){
                        doAction();
                    }
                    if(geneTimer <= 0){
                        tickGene();
                    }
                }
                genome.iterate();
            }
        }
        public void doAction(){
            useEnergy();
            Codon thisCodon = genome.codons.get(genome.rotateOn);
            int[] info = thisCodon.codonInfo;
            switch (info[0]) {
                case 1:
                    if (genome.directionOn == 0) {
                        if (info[1] == 1 || info[1] == 2) {
                            Particle foodToEat = selectParticleInCell(info[1] - 1); // digest either "food" or "waste".
                            if (foodToEat != null) {
                                eat(foodToEat);
                            }
                        } else if (info[1] == 3) { // digest "wall"
                            energy += (1 - energy) * E_RECIPROCAL * 0.2f;
                            hurtWall(26);
                            laserWall();
                        }
                    }
                    break;
                case 2:
                    if (genome.directionOn == 0) {
                        if (info[1] == 1 || info[1] == 2) {
                            Particle wasteToPushOut = selectParticleInCell(info[1] - 1);
                            if (wasteToPushOut != null) {
                                pushOut(wasteToPushOut);
                            }
                        } else if (info[1] == 3) {
                            die();
                        }
                    }
                    break;
                case 3:
                    if (genome.directionOn == 0) {
                        if (info[1] == 1 || info[1] == 2) {
                            Particle particle = selectParticleInCell(info[1] - 1);
                            shootLaserAt(particle);
                        } else if (info[1] == 3) {
                            healWall();
                        }
                    }
                    break;
                case 4:
                    switch (info[1]) {
                        case 4:
                            genome.performerOn = genome.getWeakestCodon();
                            break;
                        case 5:
                            genome.directionOn = 1;
                            break;
                        case 6:
                            genome.directionOn = 0;
                            break;
                        case 7:
                            genome.performerOn = loopItInt(genome.rotateOn + info[2], genome.codons.size());
                            break;
                    }
                    break;
                case 5:
                    if (genome.directionOn == 1 && info[1] == 7) {
                        readToMemory(info[2], info[3]);
                    }
                    break;
                case 6:
                    if (info[1] == 7 || genome.directionOn == 0) {
                        writeFromMemory(info[2], info[3]);
                    }
                    break;
            }
            genome.hurtCodons();
        }
        public void useEnergy(){
            energy = Math.max(0,energy-GENE_TICK_ENERGY);
        }
        public void readToMemory(int start, int end){
            StringBuilder memoryBuilder = new StringBuilder();
            laserTarget = null;
            laserCoor.clear();
            laserT = tickCount;
            for(int pos = start; pos <= end; pos++){
                int index = loopItInt(genome.performerOn+pos,genome.codons.size());
                Codon c = genome.codons.get(index);
                memoryBuilder.append(infoToString(c.codonInfo));
                if(pos < end){
                    memoryBuilder.append("-");
                }
                laserCoor.add(getCodonCoor(index,genome.CODON_DIST));
            }
            memory = memoryBuilder.toString();
        }
        public void writeFromMemory(int start, int end){
            if(memory.length() == 0){
                return;
            }
            laserTarget = null;
            laserCoor.clear();
            laserT = tickCount;
            if(genome.directionOn == 0){
                writeOutwards();
            }else{
                writeInwards(start,end);
            }
        }
        public void writeOutwards(){
            double theta = Math.random()*2*Math.PI;
            double ugo_vx = Math.cos(theta);
            double ugo_vy = Math.sin(theta);
            double[] startCoor = getHandCoor();
            double[] newUGOcoor = new double[]{startCoor[0],startCoor[1],startCoor[0]+ugo_vx,startCoor[1]+ugo_vy};
            Particle newUGO = new Particle(newUGOcoor,2,memory,tickCount);
            ugo.add(newUGO);
            newUGO.addToCellList();
            laserTarget = newUGO;

            String[] memoryParts = memory.split("-");
            for(int i = 0; i < memoryParts.length; i++){
                useEnergy();
            }
        }
        public void writeInwards(int start, int end){
            laserTarget = null;
            String[] memoryParts = memory.split("-");
            for(int pos = start; pos <= end; pos++){
                int index = loopItInt(genome.performerOn+pos,genome.codons.size());
                Codon c = genome.codons.get(index);
                if(pos-start < memoryParts.length){
                    String memoryPart = memoryParts[pos-start];
                    c.setFullInfo(stringToInfo(memoryPart));
                    laserCoor.add(getCodonCoor(index,genome.CODON_DIST));
                }
                useEnergy();
            }
        }
        public void healWall(){
            wallHealth += (1-wallHealth)*E_RECIPROCAL;
            laserWall();
        }
        public void laserWall(){
            laserT = tickCount;
            laserCoor.clear();
            for(int i = 0; i < 4; i++){
                double[] result = {x+(i/2F),y+(i%2)};
                laserCoor.add(result);
            }
            laserTarget = null;
        }
        public void eat(Particle food){
            if(food.type == 0){
                Particle newWaste = new Particle(food.coor,1,-99999);
                shootLaserAt(newWaste);
                newWaste.addToCellList();
                wastes.add(newWaste);
                food.removeParticle();
                energy += (1-energy)*E_RECIPROCAL;
            }else{
                shootLaserAt(food);
            }
        }
        public void shootLaserAt(Particle food){
            laserT = tickCount;
            laserTarget = food;
        }
        public double[] getHandCoor(){
            double r = genome.HAND_DIST;
            if(genome.directionOn == 0){
                r += genome.HAND_LEN;
            }else{
                r -= genome.HAND_LEN;
            }
            return getCodonCoor(genome.performerOn,r);
        }
        public double[] getCodonCoor(int i, double r){
            double theta = (i*2*PI) /(genome.codons.size())-PI/2;
            double r2 = r/BIG_FACTOR;
            double handX = x+0.5f+r2*Math.cos(theta);
            double handY = y+0.5f+r2*Math.sin(theta);
            return new double[]{handX, handY};
        }
        public void pushOut(Particle waste){
            int[][] dire = {{0,1},{0,-1},{1,0},{-1,0}};
            int chosen = -1;
            while(chosen == -1 || cells[y+dire[chosen][1]][x+dire[chosen][0]].type != 0){
                chosen = (int)random(0,4);
            }
            double[] oldCoor = waste.copyCoor();
            for(int dim = 0; dim < 2; dim++){
                if(dire[chosen][dim] == -1){
                    waste.coor[dim] = Math.floor(waste.coor[dim])-EPS;
                    waste.velo[dim] = -Math.abs(waste.velo[dim]);
                }else if(dire[chosen][dim] == 1){
                    waste.coor[dim] = Math.ceil(waste.coor[dim])+EPS;
                    waste.velo[dim] = Math.abs(waste.velo[dim]);
                }
                waste.loopCoor(dim);
            }
            Cell p_cell = getCellAt(oldCoor,true);
            p_cell.removeParticleFromCell(waste);
            Cell n_cell = getCellAt(waste.coor,true);
            n_cell.addParticleToCell(waste);
            laserT = tickCount;
            laserTarget = waste;
        }
        public void tickGene(){
            geneTimer += GENE_TICK_TIME;
            genome.rotateOn = (genome.rotateOn+1)%genome.codons.size();
        }
        public void hurtWall(double multi){
            if(type >= 2){
                wallHealth -= WALL_DAMAGE*multi;
                if(wallHealth <= 0){
                    die();
                }
            }
        }
        public void tamper(){
            if(!tampered){
                tampered = true;
                cellCounts[0]--;
                cellCounts[1]++;
            }
        }
        public void die(){
            for(int i = 0; i < genome.codons.size(); i++){
                Particle newWaste = new Particle(getCodonCoor(i,genome.CODON_DIST),1,-99999);
                newWaste.addToCellList();
                wastes.add(newWaste);
            }
            type = 0;
            if(this == selectedCell){
                selectedCell = null;
            }
            if(tampered){
                cellCounts[1]--;
            }else{
                cellCounts[0]--;
            }
            cellCounts[2]++;
        }
        public void addParticleToCell(Particle food){
            particlesInCell.get(food.type).add(food);
        }
        public void removeParticleFromCell(Particle food){
            particlesInCell.get(food.type).remove(food);
        }
        public Particle selectParticleInCell(int type){
            ArrayList<Particle> myList = particlesInCell.get(type);
            if(myList.size() == 0){
                return null;
            }else{
                int choiceIndex = (int)(Math.random()*myList.size());
                return myList.get(choiceIndex);
            }
        }
        public String getCellName(){
            if(x == -1){
                return "Custom UGO";
            }else if(type == 2){
                return "Cell at ("+x+", "+y+")";
            }else{
                return "";
            }
        }
        public int getParticleCount(int t){
            if(t == -1){
                int sum = 0;
                for(int i = 0; i < 3; i++){
                    sum += particlesInCell.get(i).size();
                }
                return sum;
            }else{
                return particlesInCell.get(t).size();
            }
        }
    }
    class Codon{
        public int[] codonInfo;
        double codonHealth;
        public Codon(int[] info, double health){
            codonInfo = info;
            codonHealth = health;
        }
        public int getColor(int p){
            return intToColor(CodonInfo.getColor(p,codonInfo[p]));
        }
        public int getTextColor(int p){
            return intToColor(CodonInfo.getTextColor(p,codonInfo[p]));
        }
        public String getText(int p){
            return CodonInfo.getText(p,codonInfo);
        }
        public boolean hasSubstance(){
            return (codonInfo[0] != 0 || codonInfo[1] != 0);
        }
        public void hurt(){
            if(hasSubstance()){
                codonHealth -= Math.random()*CODON_DEGRADE_SPEED;
                if(codonHealth <= 0){
                    codonHealth = 1;
                    codonInfo[0] = 0;
                    codonInfo[1] = 0;
                }
            }
        }
        public void setInfo(int p, int val){
            codonInfo[p] = val;
            codonHealth = 1.0f;
        }
        public void setFullInfo(int[] info){
            codonInfo = info;
            codonHealth = 1.0f;
        }
    }
    static class CodonInfo{
        public static int[][][] cols = {{{0,0,0},{100,0,200},{180,160,10},
                {0,150,0},{200,0,100},{70,70,255},
                {0,0,220}},
                {{0,0,0},{200,50,50},{100,65,0},{160,80,160},
                        {80,180,80},{0,100,100},
                        {0,200,200},{140,140,140},{90,90,90},{90,90,90}}};
        static String[][] names = {{"none","digest","remove","repair","move hand","read","write"},
                {"none","food","waste","wall","weak loc","inward","outward","RGL","- RGL start +","- RGL end +"}};
        public static int[] getColor(int p, int t){
            return CodonInfo.cols[p][t];
        }
        public static int[] getTextColor(int p, int t){
    /*int[] c = cols[p][t];
    float sum = c[0]+c[1]*2+c[2];
    if(sum < 128*3){
      int[] result = {255,255,255};
      return result;
    }else{
      int[] result = {0,0,0};
      return result;
    }*/
            return new int[]{255,255,255};
        }
        public static String getText(int p, int[] codonInfo){
            String result = CodonInfo.names[p][codonInfo[p]].toUpperCase();
            if(p == 1 && codonInfo[1] == 7){
                result += " ("+codonInfo[2]+" to "+codonInfo[3]+")";
            }
            return result;
        }
        public static String getTextSimple(int p, int t, int start, int end){
            String result = CodonInfo.names[p][t].toUpperCase();
            if(p == 1 && t == 7){
                result += " ("+start+" to "+end+")";
            }
            return result;
        }
        public static int getOptionSize(int p){
            return names[p].length;
        }
    }
    class Genome{
        ArrayList<Codon> codons;
        int rotateOn = 0;
        int performerOn = 0;
        int directionOn = 0;
        double appRO;
        double appPO;
        double appDO;
        double VISUAL_TRANSITION = 0.38f;
        float HAND_DIST = 32;
        float HAND_LEN = 7;
        double[][] codonShape = {{-2,0},{-2,2},{-1,3},{0,3},{1,3},{2,2},{2,0},{0,0}};
        double[][] telomereShape = {{-2,2},{-1,3},{0,3},{1,3},{2,2},{2,-2},{1,-3},{0,-3},{-1,-3},{-2,-2}};

        int ZERO_CH = '0';
        double CODON_DIST = 17;
        double CODON_WIDTH = 1.4f;
        boolean isUGO;

        public Genome(String s, boolean isUGOp){
            codons = new ArrayList<>();
            String[] parts = s.split("-");
            for (String part : parts) {
                int[] info = stringToInfo(part);
                codons.add(new Codon(info, 1.0f));
            }
            appRO = 0;
            appPO = 0;
            appDO = 0;
            isUGO = isUGOp;
            if(isUGO){
                CODON_DIST = 10.6f;
            }
        }
        public void iterate(){
            appRO += loopIt(rotateOn-appRO, codons.size(),true)*VISUAL_TRANSITION*PLAY_SPEED;
            appPO += loopIt(performerOn-appPO, codons.size(),true)*VISUAL_TRANSITION*PLAY_SPEED;
            appDO += (directionOn-appDO)*VISUAL_TRANSITION*PLAY_SPEED;
            appRO = loopIt(appRO, codons.size(),false);
            appPO = loopIt(appPO, codons.size(),false);
        }
        public void drawHand(){
            double appPOAngle = (float)(appPO*2*PI/codons.size());
            double appDOAngle = (float)(appDO*PI);
            strokeWeight(1);
            noFill();
            stroke(transperize(handColor,0.5f));
            ellipse(0,0,HAND_DIST*2,HAND_DIST*2);
            pushMatrix();
            rotate((float)appPOAngle);
            translate(0,-HAND_DIST);
            rotate((float)appDOAngle);
            noStroke();
            fill(handColor);
            beginShape();
            vertex(5,0);
            vertex(-5,0);
            vertex(0,-HAND_LEN);
            endShape(CLOSE);
            popMatrix();
        }
        public void drawCodons(){
            for(int i = 0; i < codons.size(); i++){
                drawCodon(i);
            }
        }
        public void drawCodon(int i){
            if(camS < ZOOM_THRESHOLD){
                return;
            }
            int VIS_GENOME_LENGTH = max(4,codons.size());
            double CODON_ANGLE = (double)(1.0f)/VIS_GENOME_LENGTH*2*PI;
            double PART_ANGLE = CODON_ANGLE/5.0f;
            double baseAngle = -PI/2+i*CODON_ANGLE;
            pushMatrix();
            rotate((float)(baseAngle));

            Codon c = codons.get(i);
            if(c.codonHealth != 1.0f){
                beginShape();
                fill(TELOMERE_COLOR);
                for (double[] cv : telomereShape) {
                    double ang = cv[0] * PART_ANGLE;
                    double dist = cv[1] * CODON_WIDTH + CODON_DIST;
                    vertex((float) (Math.cos(ang) * dist), (float) (Math.sin(ang) * dist));
                }
            }
            endShape(CLOSE);
            for(int p = 0; p < 2; p++){
                beginShape();
                fill(c.getColor(p));
                for (double[] cv : codonShape) {
                    double ang = cv[0] * PART_ANGLE * c.codonHealth;
                    double dist = cv[1] * (2 * p - 1) * CODON_WIDTH + CODON_DIST;
                    vertex((float) (Math.cos(ang) * dist), (float) (Math.sin(ang) * dist));
                }
                endShape(CLOSE);
            }
            popMatrix();
        }
        public void hurtCodons(){
            for (Codon c : codons) {
                if (c.hasSubstance()) {
                    c.hurt();
                }
            }
        }
        public int getWeakestCodon(){
            double record = 9999;
            int holder = -1;
            for(int i = 0; i < codons.size(); i++){
                double val = codons.get(i).codonHealth;
                if(val < record){
                    record = val;
                    holder = i;
                }
            }
            return holder;
        }
        public String getGenomeString(){
            StringBuilder str = new StringBuilder();
            for(int i = 0; i < codons.size(); i++){
                Codon c = codons.get(i);
                str.append(infoToString(c.codonInfo));
                if(i < codons.size()-1){
                    str.append("-");
                }
            }
            return str.toString();
        }
        public String getGenomeStringShortened(){
            int limit = max(1,codons.size()-1);
            StringBuilder str = new StringBuilder();
            for(int i = 0; i < limit; i++){
                Codon c = codons.get(i);
                str.append(infoToString(c.codonInfo));
                if(i < limit-1){
                    str.append("-");
                }
            }
            return str.toString();
        }
        public String getGenomeStringLengthened(){
            if(codons.size() == 9){
                return getGenomeString();
            }else{
                return getGenomeString()+"-00";
            }
        }
    }
    class Particle{
        double[] coor;
        double[] velo;
        int type;
        double laserX = 0;
        double laserY = 0;
        int laserT = -9999;
        int birthFrame;
        double AGE_GROW_SPEED = 0.08f;
        Genome UGO_genome;

        public Particle(double[] tcoor, int ttype, int b){
            coor = tcoor;
            velo = getRandomVelo();
            type = ttype;
            UGO_genome = null;
            birthFrame = b;
        }
        public Particle(double[] tcoor, int ttype, String genomeString, int b){
            coor = tcoor;
            double dx = tcoor[2]-tcoor[0];
            double dy = tcoor[3]-tcoor[1];
            double dist = Math.sqrt(dx*dx+dy*dy);
            double sp = Math.random()*(SPEED_HIGH-SPEED_LOW)+SPEED_LOW;
            velo = new double[]{dx/dist*sp,dy/dist*sp};
            type = ttype;
            UGO_genome = new Genome(genomeString,true);
            birthFrame = b;
        }
        public double[] copyCoor(){
            double[] result = new double[2];
            System.arraycopy(coor, 0, result, 0, 2);
            return result;
        }
        public void moveDim(int d){
            float visc = (getCellTypeAt(coor,true) == 0) ? 1 : 0.5f;
            double[] future = copyCoor();
            future[d] = coor[d]+velo[d]*visc*PLAY_SPEED;
            if(cellTransfer(coor, future)){
                int currentType = getCellTypeAt(coor,true);
                int futureType = getCellTypeAt(future,true);
                if(type == 2 && currentType == 0 && futureType == 2 &&
                        UGO_genome.codons.size()+getCellAt(future,true).genome.codons.size() <= MAX_CODON_COUNT){ // there are few enough codons that we can fit in the new material!
                    injectGeneticMaterial(future);  // UGO is going to inject material into a cell!
                }else if(futureType == 1 ||
                        (type >= 1 && (currentType != 0 || futureType != 0))){ // bounce
                    Cell b_cell = getCellAt(future,true);
                    if(b_cell.type >= 2){
                        b_cell.hurtWall(1);
                    }
                    if(velo[d] >= 0){
                        velo[d] = -Math.abs(velo[d]);
                        future[d] = Math.ceil(coor[d])-EPS;
                    }else{
                        velo[d] = Math.abs(velo[d]);
                        future[d] = Math.floor(coor[d])+EPS;
                    }
                    Cell t_cell = getCellAt(coor,true);
                    if(t_cell.type >= 2){
                        t_cell.hurtWall(1);
                    }
                }else{
                    while(future[d] >= WORLD_SIZE){
                        future[d] -= WORLD_SIZE;
                    }
                    while(future[d] < 0){
                        future[d] += WORLD_SIZE;
                    }
                    hurtWalls(coor, future);
                }
            }
            coor = future;
        }
        public void injectGeneticMaterial(double[] futureCoor){
            Cell c = getCellAt(futureCoor,true);
            int injectionLocation = c.genome.rotateOn;
            ArrayList<Codon> toInject = UGO_genome.codons;
            int INJECT_SIZE = UGO_genome.codons.size();

            for(int i = 0; i < toInject.size(); i++){
                int[] info = toInject.get(i).codonInfo;
                c.genome.codons.add(injectionLocation+i,new Codon(info,1.0f));
            }
            if(c.genome.performerOn >= c.genome.rotateOn){
                c.genome.performerOn += INJECT_SIZE;
            }
            c.genome.rotateOn += INJECT_SIZE;
            c.tamper();
            removeParticle();
            Particle newWaste = new Particle(coor,1,-99999);
            newWaste.addToCellList();
            wastes.add(newWaste);
        }
        public void hurtWalls(double[] coor, double[] future){
            Cell p_cell = getCellAt(coor,true);
            if(p_cell.type >= 2){
                p_cell.hurtWall(1);
            }
            p_cell.removeParticleFromCell(this);
            Cell n_cell = getCellAt(future,true);
            if(n_cell.type >= 2){
                n_cell.hurtWall(1);
            }
            n_cell.addParticleToCell(this);
        }
        public void iterate(){
            for(int dim = 0; dim < 2; dim++){
                moveDim(dim);
            }
        }
        public void drawParticle(double x, double y, double s){
            pushMatrix();
            translate((float)x,(float)y);

            double ageScale = Math.min(1.0f,(tickCount-birthFrame)*AGE_GROW_SPEED);
            scale((float)(s/BIG_FACTOR*ageScale));
            noStroke();
            float size = 0.1f;
            switch (type) {
                case 0:
                    fill(255, 0, 0);
                    size = 0.11f;
                    break;
                case 1:
                    fill(WASTE_COLOR);
                    size = 0.09f;
                    break;
                case 2:
                    size = 0.1f;
                    fill(0);
                    break;
            }
            ellipseMode(CENTER);
            ellipse(0,0,size*BIG_FACTOR,size*BIG_FACTOR);
            if(UGO_genome != null){
                UGO_genome.drawCodons();
            }
            popMatrix();
        }

        public void removeParticle(){
            particles[type].remove(this);
            getCellAt(coor,true).particlesInCell.get(type).remove(this);
        }
        public void addToCellList(){
            Cell cellIn = getCellAt(coor,true);
            cellIn.addParticleToCell(this);
        }
        public void loopCoor(int d){
            while(coor[d] >= WORLD_SIZE){
                coor[d] -= WORLD_SIZE;
            }
            while(coor[d] < 0){
                coor[d] += WORLD_SIZE;
            }
        }
    }
    public void settings() {  size(1728,972);  /* noSmooth(); */ smooth(2); }
    static public void main(String[] passedArgs) {
        String[] appletArgs = new String[] { "remix.Virus" };
        if (passedArgs != null) {
            PApplet.main(concat(appletArgs, passedArgs));
        } else {
            PApplet.main(appletArgs);
        }
    }

    public void rectSmooth(float a, float b, float c, float d) {
        float smooth = (float) (101F/camS);
        super.rect(a-smooth, b-smooth, c+(smooth*2), d+(smooth*2));
    }

    @Override
    public void keyPressed(KeyEvent event) {
        switch (event.getKeyCode()) {
            case DOWN:
                down = true;
                break;
            case UP:
                up = true;
                break;
            case LEFT:
                left = true;
                break;
            case RIGHT:
                right = true;
                break;
        }
    }

    @Override
    public void keyReleased(KeyEvent event) {
        switch (event.getKeyCode()) {
            case DOWN:
                down = false;
                break;
            case UP:
                up = false;
                break;
            case LEFT:
                left = false;
                break;
            case RIGHT:
                right = false;
                break;
        }
    }
}