package model;

public class ReduceLROnPlateau {

    private int patience; // Number of episodes with no improvement after which learning rate will be reduced.
    private double factor; // Factor by which the learning rate will be reduced. new_lr = lr * factor
    private double minLr; // Minimum learning rate
    private double bestValue; // Best observed value (could be loss or reward)
    private int wait; // Number of episodes since bestValue was last updated

    public ReduceLROnPlateau(int patience, double factor, double minLr, double initialBestValue) {
        this.patience = patience;
        this.factor = factor;
        this.minLr = minLr;
        this.bestValue = initialBestValue;
        this.wait = 0;
    }

    public double adjustLearningRate(double currentScore, double currentLr) {
        if (currentScore < bestValue && currentScore > 0 ) { // monitoring loss
            bestValue = currentScore;
            wait = 0;
        } else {
            wait++;
            if (wait >= patience) {
                wait = 0;
                currentLr = Math.max(currentLr * factor, minLr);
            }
        }
        return currentLr;
    }
}

