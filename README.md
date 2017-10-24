This project uses reinforcement learning (RL) to train an agent to play a FlappyBird-like game.

Here is a video of it on <a href='https://youtu.be/3Y8ckBK8afw'>YouTube</a>
 and this one includes the <a href='https://youtu.be/ajNVe7BtktQ'>training</a>. Skip to 3:26 to see the result only.

The algorithm used is called Q-Learning and jMonkeyEngine 3 is the game engine behind the game. Exploration is done 1% of the time. Timesteps 6,000. Increasing exploration frequency may make it fail to converge.

Instead of a table populated with states and actions, a Map is used and it gets populated as the agent learns. The state-actions are used as the keys.
