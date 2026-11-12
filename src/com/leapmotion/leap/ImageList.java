package com.leapmotion.leap;

import java.util.List;

/** @author Tiago Martins */
public class ImageList extends LeapList<Image> {

    public ImageList() { super(null); }

    ImageList(List<Image> i) { super(i); }

    @Override
    protected Image invalidItem() { return Image.invalid(); }
}
