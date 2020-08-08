#!/usr/bin/env python3

import math

import torch
import torch.nn as nn

import pandas as pd

import numpy as np


class Neural_Network(nn.Module):

    def __init__(self, inputSize, outputSize, hiddenSize):
        super(Neural_Network, self).__init__()
        self.inputSize = inputSize
        self.outputSize = outputSize
        self.hiddenSize = hiddenSize
        
        self.W1 = torch.randn(self.inputSize, self.hiddenSize)
        self.W2 = torch.randn(self.hiddenSize, self.outputSize)
        
    def forward(self, X):
        self.z = torch.matmul(X, self.W1)
        self.z2 = torch.sigmoid(self.z)
        self.z3 = torch.matmul(self.z2, self.W2)
        o = torch.sigmoid(self.z3)
        return o
        
    def sigmoid_der(self, s):
        return s * (1 - s)
    
    def backward(self, X, y, o):
        self.o_error = y - o
        self.o_delta = self.o_error * self.sigmoid_der(o)
        self.z2_error = torch.matmul(self.o_delta, torch.t(self.W2))
        self.z2_delta = self.z2_error * self.sigmoid_der(self.z2)
        self.W1 += torch.matmul(torch.t(X), self.z2_delta)
        self.W2 += torch.matmul(torch.t(self.z2), self.o_delta)
        
    def train_model(self, X, y):
        o = self.forward(X)
        self.backward(X, y, o)
        

if __name__ == '__main__':
    
    df = pd.read_csv('data/1.csv', header=None)
    # Shuffle data
    df = df.sample(frac=1).reset_index(drop=True)

    data = df.iloc[:, :-1] 
    labels = df.iloc[:, -1]

    X = torch.from_numpy(data.values.astype(np.float32))

    y = torch.zeros(len(labels), 7)
    for i, label in enumerate(labels):
        y[i, label] = 1 
    
    # Scale / normalize values
    #X_max, _ = torch.max(X, 0)
    #X = torch.div(X, X_max)

    NN = Neural_Network(28, 7, 18)
    for i in range(1000):
        print ("#" + str(i) + " Loss: " + str(torch.mean((y - NN(X))**2).detach().item()))  # mean sum squared loss
        NN.train_model(X, y)


    # Export the model
    dummy_input = torch.randn(28)
    torch.onnx.export(NN,                                  # model being run
                      dummy_input,                         # model input (or a tuple for multiple inputs)
                      "models/gesture-classifier.onnx",    # where to save the model (can be a file or file-like object)
                      verbose=True,
                      input_names = ['input'],   # the model's input names
                      output_names = ['output'] # the model's output names
                      )
