#!/usr/bin/env python3
# /usr/bin/env works with anaconda
#
# Contributed by a forgotten user
#   in a conversation on reddit

from tensorflow.keras.layers import Layer
import tensorflow.keras.backend as K

class Nathan(Layer):
  def __init__(self, **kwargs):
        super(Nathan, self).__init__(**kwargs)

  def build(self, input_shape):
        # Create a trainable weight variable for this layer.
        i = int(input_shape[len(input_shape)-1])
        self.alpha = self.add_weight(name='alpha',
                             	shape=(1,),
                             	initializer='RandomNormal',
                             	trainable=True)
        super(Nathan, self).build(input_shape)  # Be sure to call this at the end

  def call(self, x):
        return x * K.sigmoid(x) * (1 + self.alpha*K.exp(-x))
    
  def compute_output_shape(self, input_shape):
        return input_shape

