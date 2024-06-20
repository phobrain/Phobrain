package org.phobrain.util;

/**
 **  SPDX-FileCopyrightText: 2009 Andreas Dolk <https://stackoverflow.com/users/105224/andreas-dolk>
 **
 **  SPDX-License-Identifier: CC-BY-SA-4.0
 **/

// https://stackoverflow.com/questions/1194656/appending-to-an-objectoutputstream

import java.io.OutputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;

public class AppendingObjectOutputStream extends ObjectOutputStream {

  public AppendingObjectOutputStream(OutputStream out) throws IOException {
    super(out);
  }

  @Override
  protected void writeStreamHeader() throws IOException {
    // do not write a header, but reset:
    // this line added after another question
    // showed a problem with the original
    reset();
  }

}
