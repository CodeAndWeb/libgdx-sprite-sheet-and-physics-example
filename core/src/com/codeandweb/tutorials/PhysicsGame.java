package com.codeandweb.tutorials;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g3d.utils.ShapeCache;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.codeandweb.physicseditor.PhysicsShapeCache;

import java.util.HashMap;
import java.util.Random;

public class PhysicsGame extends ApplicationAdapter {
  /**
   * This affects the speed of our simulation, and how gravity behaves. This is
   * set to our game's expected FPS rate for optimal performance for what we're
   * doing in this tutorial. If you were simulating something that required
   * greater precision, such as planets orbiting a star, you would want to set
   * this to as high as double the frame rate, or 1/120.
   * <p/>
   * Setting it to a higher rate will result in a smoother, but slower
   * simulation. Setting it to a lower value will result in a choppy frame
   * rate, but increase the amount of polygons the simulation can process.
   */
  static final float STEP_TIME = 1f / 60f;

  /**
   * Velocity iterations will improve the stability of the physics simulation.
   * A higher value will provide greater precision for collision detection, at
   * the cost of consuming more of the CPU.
   */
  static final int VELOCITY_ITERATIONS = 6;

  /**
   * This affects the way bodies react to collisions. A higher value improves
   * the simulations overlap resolution.
   * <p/>
   * I recommend reading this article on the anatomy of a collision for a
   * clearer understanding of both velocity and position iterations:
   * http://www.iforce2d.net/b2dtut/collision-anatomy
   */
  static final int POSITION_ITERATIONS = 2;

  /**
   * This is a scalar used to make our sprites fit within the physics
   * simulation. Without it the sprites would be too big to be drawn on the
   * screen.
   */
  static final float SCALE = 0.05f;

  /**
   * Adjust this value to change the amount of fruit that falls from the sky.
   */
  static final int COUNT = 25;

  /**
   * Used to cache our sprites so we only have to load them in when the game
   * first starts. For a production-worthy game I would recommend using a
   * {@link com.badlogic.gdx.assets.AssetManager} instead of this.
   */
  final HashMap<String, Sprite> sprites = new HashMap<String, Sprite>();

  /**
   * Used to draw the collision polygons to the screen for debugging purposes.
   * This is disabled by default, but can be enabled by uncommenting a line in
   * {@link #render()}.
   */
  Box2DDebugRenderer debugRenderer;

  /**
   * Parses XML data exported from PhysicsEditor into Box2D bodies.
   */
  PhysicsShapeCache physicsBodies;

  /**
   * Used to convert our sprite sheet found at android/assets/sprites.png and
   * described in android/assets/sprites.txt into {@link Sprite} objects.
   */
  TextureAtlas textureAtlas;

  /**
   * Used to draw the sprites to the screen.
   */
  SpriteBatch batch;

  /**
   * A 2D camera. This is required for scaling our sprites. It is managed by
   * {@link #viewport}.
   */
  OrthographicCamera camera;

  /**
   * Provides scaling while maintaining an aspect ratio when the screen is
   * resized.
   */
  ExtendViewport viewport;

  /**
   * Our Box2D physics world.
   */
  World world;

  /**
   * Used to fix our physics step time. You can read more on what that means in
   * this article: http://gafferongames.com/game-physics/fix-your-timestep/
   */
  float accumulator = 0;

  /**
   * A physics body for the ground. This is a static body that does not move.
   * It spans the width of our game's screen, and is 1 unit tall.
   */
  Body ground;

  /**
   * Stores the physics bodies of the fruits that fall from the sky.
   */
  Body[] fruitBodies = new Body[COUNT];

  /**
   * Stores pointers to the sprites contained in {@link #sprites} that match
   * the bodies in {@link #fruitBodies}.
   */
  Sprite[] fruitSprites = new Sprite[COUNT];

  @Override
  public void create() {
    Box2D.init();

    batch = new SpriteBatch();

    camera = new OrthographicCamera();

    viewport = new ExtendViewport(50, 50, camera);

    textureAtlas = new TextureAtlas("sprites.txt");

    loadSprites();

    world = new World(new Vector2(0, -120), true);

    physicsBodies = new PhysicsShapeCache("physics.xml");

    debugRenderer = new Box2DDebugRenderer();

    generateFruit();
  }

  /**
   * Loads the sprites and caches them into {@link #sprites}.
   */
  private void loadSprites() {
    Array<AtlasRegion> regions = textureAtlas.getRegions();

    for (AtlasRegion region : regions) {
      Sprite sprite = textureAtlas.createSprite(region.name);

      float width = sprite.getWidth() * SCALE;
      float height = sprite.getHeight() * SCALE;

      sprite.setSize(width, height);
      sprite.setOrigin(0, 0);

      sprites.put(region.name, sprite);
    }
  }

  /**
   * Populates {@link #fruitBodies} and {@link #fruitSprites}.
   */
  private void generateFruit() {
    String[] fruitNames = new String[]{"banana", "cherries", "orange"};

    Random random = new Random();

    for (int i = 0; i < fruitBodies.length; i++) {
      String name = fruitNames[random.nextInt(fruitNames.length)];

      fruitSprites[i] = sprites.get(name);

      float x = random.nextFloat() * 50;
      float y = random.nextFloat() * 50 + 50;

      fruitBodies[i] = createBody(name, x, y, 0);
    }
  }

  /**
   * Uses {@link ShapeCache} to parse a body described in android/assets/physics.xml
   * into a Box2D {@link Body}.
   *
   * @param name     The name of the body exactly as it appears in the XML.
   * @param x        The body's initial X position in meters.
   * @param y        The body's initial Y position in meters.
   * @param rotation The body's initial rotation in radians.
   * @return A Box2D {@link Body}.
   */
  private Body createBody(String name, float x, float y, float rotation) {
    BodyDef bodyDef = new BodyDef();
    bodyDef.type = BodyDef.BodyType.DynamicBody;

    Body body = physicsBodies.createBody(name, world, bodyDef, SCALE, SCALE);
    body.setTransform(x, y, rotation);

    return body;
  }

  /**
   * This is called when the application is resized, and can happen at any
   * time, but will never be called before {@link #create()}.
   *
   * @param width  The screen's new width in pixels.
   * @param height The screen's new height in pixels.
   */
  @Override
  public void resize(int width, int height) {
    viewport.update(width, height, true);

    batch.setProjectionMatrix(camera.combined);

    createGround();
  }

  /**
   * Creates the static ground {@link Body}. Without this the fruit would
   * continue to fall indefinitely.
   */
  private void createGround() {
    if (ground != null) world.destroyBody(ground);

    BodyDef bodyDef = new BodyDef();

    bodyDef.type = BodyDef.BodyType.StaticBody;

    FixtureDef fixtureDef = new FixtureDef();
    fixtureDef.friction = 1;

    PolygonShape shape = new PolygonShape();

    shape.setAsBox(camera.viewportWidth, 1);

    fixtureDef.shape = shape;

    ground = world.createBody(bodyDef);
    ground.createFixture(fixtureDef);

    ground.setTransform(0, 0, 0);

    shape.dispose();
  }

  /**
   * Called once per frame to render the game. You can use
   * {@code Gdx.graphics.getDeltaTime()} to find out how much time in seconds
   * has passed between the current and last frame.
   */
  @Override
  public void render() {
    // Clear the screen using a sky-blue background.
    Gdx.gl.glClearColor(0.57f, 0.77f, 0.85f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

    // Step the physics world.
    stepWorld();

    // open the sprite batch buffer for drawing
    batch.begin();

    // iterate through each of the fruits
    for (int i = 0; i < fruitBodies.length; i++) {

      // get the physics body of the fruit
      Body body = fruitBodies[i];

      // get the position of the fruit from Box2D
      Vector2 position = body.getPosition();

      // get the degrees of rotation by converting from radians
      float degrees = (float) Math.toDegrees(body.getAngle());

      // draw the fruit on the screen
      drawSprite(fruitSprites[i], position.x, position.y, degrees);
    }

    // close the buffer - this is what actually draws the sprites
    batch.end();

    // uncomment to show the physics polygons
    // debugRenderer.render(world, camera.combined);
  }

  /**
   * Steps the physics simulation. This is called every render frame.
   */
  private void stepWorld() {
    float delta = Gdx.graphics.getDeltaTime();

    accumulator += Math.min(delta, 0.25f);

    if (accumulator >= STEP_TIME) {
      accumulator -= STEP_TIME;

      world.step(STEP_TIME, VELOCITY_ITERATIONS, POSITION_ITERATIONS);
    }
  }

  /**
   * Sets the position and rotation of a sprite, and draws it to the supplied
   * {@link SpriteBatch}.
   *
   * @param sprite  The sprite to draw.
   * @param x       X position in meters.
   * @param y       Y position in meters.
   * @param degrees Degrees to rotate the sprite.
   */
  private void drawSprite(Sprite sprite, float x, float y, float degrees) {
    sprite.setPosition(x, y);

    sprite.setRotation(degrees);

    sprite.draw(batch);
  }

  /**
   * Frees up all the game's resources. This is called when the game closes.
   */
  @Override
  public void dispose() {
    textureAtlas.dispose();

    sprites.clear();

    world.dispose();

    debugRenderer.dispose();
  }
}

