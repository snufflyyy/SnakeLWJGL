import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import java.nio.*;
import java.util.ArrayList;
import java.util.Random;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Game {
    private static final CharSequence[] vertexShaderSource = {
            "#version 330 core\n",
            "layout (location = 0) in vec3 aPos;\n",
            "uniform mat4 transform;\n",
            "uniform mat4 projection;\n",
            "void main()\n",
            "{\n",
            "   gl_Position = projection * transform * vec4(aPos.x, aPos.y, aPos.z, 1.0);\n",
            "}\0"
    };
    private static final CharSequence[] fragmentShaderSource = {
            "#version 330 core\n",
            "out vec4 FragColor;\n",
            "uniform vec4 color;\n",
            "void main()\n",
            "{\n",
            "   FragColor = color;\n",
            "}\0"
    };

    private static final float[] triangleVertices = {
            -0.5f, -0.5f, 0.0f, // bottom left
            0.5f, -0.5f, 0.0f, // bottom right
            -0.5f, 0.5f, 0.0f, // top left
            0.5f, 0.5f, 0.0f  // top right
    };
    private static final int[] triangleIndices = {
            0, 1, 2,
            2, 1, 3
    };

    private static final int width = 800;
    private static final int height = 600;

    private static final int tileScale = 20;

    private long window;

    private int shaderProgram;

    private int VAO;

    private int colorLocation;
    private int transformLocation;
    private int projectionLocation;

    // honestly not to sure why you need to buffer shit like this in lwjgl
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

    //////////////////////////////////////////////
    // game related stuff                       //
    //////////////////////////////////////////////

    static enum direction {
        NONE,
        UP,
        DOWN,
        LEFT,
        RIGHT
    }

    private boolean gameOver = false;

    private double lastMoveTime;
    private final float moveSpeed = 0.1f;

    private int score = 0;

    class Snake {
        Vector2f headPosition;
        Matrix4f transform;
        ArrayList<Vector2f> body;
        direction direction;

        public Snake() {
            // puts the snake's head in the middle of the window
            headPosition = new Vector2f((float) width / 2 - (float) tileScale / 2, (float) height / 2 - (float) tileScale / 2);

            transform = new Matrix4f()
                    .translate(headPosition.x, headPosition.y, 0.0f)
                    .scale(tileScale, tileScale, 0);

            body = new ArrayList<>();
            direction = Game.direction.NONE;
        }

        public void input() {
            if (glfwGetKey(window, GLFW_KEY_UP) == GLFW_PRESS && direction != Game.direction.DOWN) {
                direction = Game.direction.UP;
            }
            if (glfwGetKey(window, GLFW_KEY_DOWN) == GLFW_PRESS && direction != Game.direction.UP) {
                direction = Game.direction.DOWN;
            }
            if (glfwGetKey(window, GLFW_KEY_LEFT) == GLFW_PRESS && direction != Game.direction.LEFT) {
                direction = Game.direction.LEFT;
            }
            if (glfwGetKey(window, GLFW_KEY_RIGHT) == GLFW_PRESS && direction != Game.direction.RIGHT) {
                direction = Game.direction.RIGHT;
            }
        }

        public void update() {
            if (glfwGetTime() - lastMoveTime >= moveSpeed) {
                // moves snake head
                switch (direction) {
                    case UP:
                        snake.headPosition.y += tileScale;
                        break;
                    case DOWN:
                        snake.headPosition.y -= tileScale;
                        break;
                    case LEFT:
                        snake.headPosition.x -= tileScale;
                        break;
                    case RIGHT:
                        snake.headPosition.x += tileScale;
                        break;
                }

                // adds to the tail if the snakebody size isnt the same as the score
                if (direction != Game.direction.NONE && score != 0) {
                    if (body.size() != score) {
                        body.addFirst(new Vector2f(headPosition.x, headPosition.y));
                    }

                    // checks to see his the score is the same as the size, if so remove the last snake tail peice thing
                    if (body.size() == score) {
                        body.removeLast();
                    }
                }

                transform.translation(snake.headPosition.x, snake.headPosition.y, 0.0f);
                transform.scale(tileScale, tileScale, 0.0f);
                lastMoveTime = glfwGetTime();
            }
        }
    }

    private Snake snake;

    private Vector2f apple;

    public void run() {
        init();
        loop();

        // Free the window callbacks and destroy the window
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);

        // Terminate GLFW and free the error callback
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private void init() {
        // Setup an error callback. The default implementation
        // will print the error message in System.err.
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Configure GLFW
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE); // the window will be resizable

        // Create the window
        window = glfwCreateWindow(width, height, "Snake", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        // Setup a key callback. It will be called every time a key is pressed, repeated or released.
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE)
                glfwSetWindowShouldClose(window, true); // We will detect this in the rendering loop
        });

        // Get the thread stack and push a new frame
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1); // int*
            IntBuffer pHeight = stack.mallocInt(1); // int*

            // Get the window size passed to glfwCreateWindow
            glfwGetWindowSize(window, pWidth, pHeight);

            // Get the resolution of the primary monitor
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            // Center the window
            glfwSetWindowPos(
                    window,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2
            );
        }

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);

        // Make the window visible
        glfwShowWindow(window);

        //////////////////////////////////////////////
        // game related stuff                       //
        //////////////////////////////////////////////

        snake = new Snake();

        Random rand = new Random();

        apple = new Vector2f((rand.nextInt(width / tileScale) + 1) * tileScale - (float) tileScale / 2, (rand.nextInt(height / tileScale) + 1) * tileScale - (float) tileScale / 2);
    }

    private void createShaders() {
        int vertexShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vertexShader, vertexShaderSource);
        GL20.glCompileShader(vertexShader);

        int fragmentShader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fragmentShader, fragmentShaderSource);
        GL20.glCompileShader(fragmentShader);

        shaderProgram = GL20.glCreateProgram();
        GL20.glAttachShader(shaderProgram, vertexShader);
        GL20.glAttachShader(shaderProgram, fragmentShader);
        GL20.glLinkProgram(shaderProgram);

        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);

        GL20.glUseProgram(shaderProgram);
        colorLocation = GL20.glGetUniformLocation(shaderProgram, "color");
        transformLocation = GL20.glGetUniformLocation(shaderProgram, "transform");
        projectionLocation = GL20.glGetUniformLocation(shaderProgram, "projection");
        GL20.glUseProgram(0);
    }

    private void createObjects() {
        VAO = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(VAO);

        // triangle vertices
        FloatBuffer triangleVerticesBuffer = stackMallocFloat(triangleVertices.length * 3);
        triangleVerticesBuffer.put(triangleVertices);
        triangleVerticesBuffer.flip();

        int VBO = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, VBO);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, triangleVerticesBuffer, GL15.GL_STATIC_DRAW);

        // position
        GL30.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0L);
        GL30.glEnableVertexAttribArray(0);

        // triangle indices
        IntBuffer triangleIndicesBuffer = stackMallocInt(triangleIndices.length);
        triangleIndicesBuffer.put(triangleIndices);
        triangleIndicesBuffer.flip();

        int EBO = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, EBO);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, triangleIndicesBuffer, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        GL30.glBindVertexArray(0);
    }

    private void drawRectangle(Vector2f position, Vector4f color) {
        Matrix4f transform = new Matrix4f()
                .translation(position.x, position.y, 0.0f)
                .scale(tileScale, tileScale, 0.0f);

        GL20.glUniform4f(colorLocation, color.x, color.y, color.z, color.w);
        GL20.glUniformMatrix4fv(transformLocation, false, transform.get(matrixBuffer));

        GL20.glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
    }

    private void loop() {
        GL.createCapabilities();

        // Set the clear color
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        createShaders();
        createObjects();

        Matrix4f projection = new Matrix4f().ortho2D(0, width, 0, height);

        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            GL20.glUseProgram(shaderProgram);
            GL30.glBindVertexArray(VAO);

            if (!gameOver) {
                snake.input();
                snake.update();
            }

            for (int i = 0; i < snake.body.size(); i++) {
                if (i != 0 && (int) snake.headPosition.x == (int) snake.body.get(i).x && (int) snake.headPosition.y == (int) snake.body.get(i).y) {
                    gameOver = true;
                }
            }

            if ((int) snake.headPosition.x == (int) apple.x && (int) snake.headPosition.y == (int) apple.y) {
                Random rand = new Random();

                score += 5;
                apple.set((rand.nextInt(width / tileScale) + 1) * tileScale - (float) tileScale / 2, (rand.nextInt(height / tileScale) + 1) * tileScale - (float) tileScale / 2);
            }

            if (snake.headPosition.x < 0 || snake.headPosition.x > width) {
                gameOver = true;
            }

            if (snake.headPosition.y < 0 || snake.headPosition.y > height) {
                gameOver = true;
            }

            GL20.glUniformMatrix4fv(projectionLocation, false, projection.get(matrixBuffer));

            if (score != 0) {
                for (Vector2f tailPiece : snake.body) {
                    drawRectangle(tailPiece, new Vector4f(0.0f, 0.25f, 1.0f, 0.0f));
                }
            }

            drawRectangle(apple, new Vector4f(1.0f, 0.0f, 0.0f, 0.0f));

            drawRectangle(snake.headPosition, new Vector4f(0.0f, 0.5f, 1.0f, 1.0f));

            GL30.glBindVertexArray(0);
            GL20.glUseProgram(0);

            glfwSwapBuffers(window); // swap the color buffers
            glfwPollEvents();
        }
    }

    public static void main(String[] args) {
        new Game().run();
    }
}