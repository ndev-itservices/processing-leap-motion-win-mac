package de.voidplus.leapmotion;

import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PImage;
import processing.core.PVector;


public class Image extends PImage implements RawAccess<com.leapmotion.leap.Image> {

    protected PApplet parent;
    protected LeapMotion leap;
    private com.leapmotion.leap.Image _image;
    protected int id;

    public Image(PApplet parent, LeapMotion leap, com.leapmotion.leap.Image _image) {
        super(_image.width(), _image.height(), PConstants.RGB);

        this.parent = parent;
        this.leap = leap;

        this._image = _image;
        this.id = _image.id();

        // https://developer.leapmotion.com/documentation/skeletal/java/devguide/Leap_Images.html
        this.loadPixels();
        byte[] imageData = this._image.data();
        int r, g, b;
        for (int i = 0; i < _image.width() * _image.height(); i++) {
            r = (imageData[i] & 0xFF) << 16;
            g = (imageData[i] & 0xFF) << 8;
            b = imageData[i] & 0xFF;
            this.pixels[i] = r | g | b;
        }
        this.updatePixels();
    }

    /**
     * Is it a valid Image object?
     *
     * @return Is it a valid Image object?
     */
    @Override
    public boolean isValid() {
        return this._image.isValid();
    }

    /**
     * Get the raw instance of com.leapmotion.leap.Image.
     *
     * @return Raw instance of com.leapmotion.leap.Image
     */
    @Override
    public com.leapmotion.leap.Image getRaw() {
        return this._image;
    }

    /**
     * Get image width.
     *
     * @return Width
     */
    public int getWidth() {
        return this.width;
    }

    /**
     * Get image height.
     *
     * @return Height
     */
    public int getHeight() {
        return this.height;
    }

    /**
     * Get the id of image.
     *
     * @return ID
     */
    public int getId() {
        return this.id;
    }

    /**
     * Is this the left camera?
     *
     * @return Is this the left camera?
     */
    public boolean isLeft() {
        return this.getId() == 1;
    }

    /**
     * Is this the right camera?
     *
     * @return Is this the right camera?
     */
    public boolean isRight() {
        return this.getId() == 0;
    }

    /**
     * Project a raw 3D position (millimetres, as returned by getRawPosition(), getRawPrevJoint(),
     * ...) onto this camera image. Corrects for the lens distortion of the camera.
     *
     * @param rawPosition Position in Leap Motion coordinates (mm)
     * @return Pixel coordinates in this image, or null if the point cannot be projected
     * (behind the camera, or no image received yet)
     */
    public PVector project(PVector rawPosition) {
        com.leapmotion.leap.Vector p = this._image.project(
                new com.leapmotion.leap.Vector(rawPosition.x, rawPosition.y, rawPosition.z));
        if (Float.isNaN(p.getX()) || Float.isNaN(p.getY())) {
            return null;
        }
        return new PVector(p.getX(), p.getY());
    }

    /**
     * Project a raw 3D position onto this image and scale the result to a drawn size.
     *
     * @param rawPosition Position in Leap Motion coordinates (mm)
     * @param drawX       X where the image is drawn
     * @param drawY       Y where the image is drawn
     * @param drawWidth   Width the image is drawn with
     * @param drawHeight  Height the image is drawn with
     * @return Screen coordinates, or null if the point cannot be projected
     */
    public PVector project(PVector rawPosition, float drawX, float drawY, float drawWidth, float drawHeight) {
        PVector p = this.project(rawPosition);
        if (p == null) {
            return null;
        }
        return new PVector(drawX + p.x * drawWidth / this.width, drawY + p.y * drawHeight / this.height);
    }

}
