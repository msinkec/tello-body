#!/usr/bin/env python3

import sys
import math
import numpy as np
import matplotlib.pyplot as plt


def augment(points, label):
    
    # Translate points, so they will be centered around (0,0).
    points -= 48
    
    # Add homogeneus coordinates
    points_ext = np.c_[points, np.ones(14)]

   
    # How a transform_mat looks like:
    #    [scale*math.cos(angle), -math.sin(angle),       tx]
    #    [math.sin(angle),       scale*math.cos(angle),  ty]
    #    [0,                     0,                       1]

    transform_mat = np.identity(3)

    for angle in range(-15, 15, 5):
        angle = (angle / 360) * 2*math.pi   # Convert to radians

        for scale in [x / 10.0 for x in range(5, 15, 2)]:
            for tx in  range(-60, 60, 10):
                for ty in range(-40, 40, 10):
                    transform_mat[0, 0] = scale * math.cos(angle)
                    transform_mat[0, 1] = -math.sin(angle)
                    transform_mat[0, 2] = tx
                    transform_mat[1, 0] = math.sin(angle)
                    transform_mat[1, 1] = scale*math.cos(angle)
                    transform_mat[1, 2] = ty

                    res = transform_mat.dot(points_ext.T).astype(int).T
                    res = np.delete(res, -1, axis=1)

                    # Translate back to initial position.
                    res += 48
                    
                    # Check if some of the points is out of range.
                    # If it is, don't store the results.
                    if (res < 96).all() and (res >= 0).all():

                        tokens = []
                        for point in res.flatten():
                            tokens.append(str(point))
                        tokens.append(label)
                        print(','.join(tokens))

                        #image = np.zeros((96, 96))
                        #for point in res:
                        #    image[point[1], point[0]] = 1
                        #plt.imshow(image, cmap='gray') 
                        #plt.show()
    


if __name__ == '__main__':

    with open(sys.argv[1], 'r') as f:

        for line in f:
            vals = line.split(',')

            vals = [int(float(x)) for x in vals]

            label = vals[-1]
            coords = vals[:-1]

            points = []
            for i, coord in enumerate(coords):
                if i % 2 == 0:
                    points.append([coord, 0])
                else:
                    points[-1][-1] = coord

            points = np.array(points)

            augment(points, str(label))

