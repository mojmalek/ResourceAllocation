package model;

import gurobi.*;

public class GurobiOptimizer {

    // Define parameters
    int numAgents;  // m
    int numTasks;  // n
    int numResourceTypes;  // l
    int packageSize;

    long[] util;  // Utility for each task
    long[][] distance;  // Distance between each agent and task location
    long[][] requirement;  // Resource requirement of each task for each resource type
    long[][] resource;  // Resources of each type available to each agent

    public GurobiOptimizer(int numAgents, int numTasks, int numResourceTypes, int packageSize, long[] util, long[][] distance, long[][] requirement, long[][] resource) {
        this.numAgents = numAgents;
        this.numTasks = numTasks;
        this.numResourceTypes = numResourceTypes;
        this.packageSize = packageSize;
        this.util = util;
        this.distance = distance;
        this.requirement = requirement;
        this.resource = resource;
    }


    public double run() {

        double objectiveValue = 0;

        try {
            GRBEnv env = new GRBEnv(true);
            env.start();

            GRBModel model = new GRBModel(env);

            // Define variables
            GRBVar[] y = model.addVars(numTasks, GRB.BINARY);

            GRBVar[][][] x = new GRBVar[numAgents][numTasks][numResourceTypes];

            GRBVar[][][] xPositive = new GRBVar[numAgents][numTasks][numResourceTypes]; // Binary indicator if x > 0

            // Additional variables to model the number of packages
//            GRBVar[][][] numberOfPackages = new GRBVar[numAgents][numTasks][numResourceTypes];

//            GRBVar[][][] xRemainder = new GRBVar[numAgents][numTasks][numResourceTypes]; // Binary indicator for remainder


            for (int i = 0; i < numAgents; i++) {
                for (int j = 0; j < numTasks; j++) {
                    for (int k = 0; k < numResourceTypes; k++) {
                        x[i][j][k] = model.addVar(0.0, requirement[j][k], 0.0, GRB.INTEGER, "x_" + i + "_" + j + "_" + k);
                        xPositive[i][j][k] = model.addVar(0, 1, 0, GRB.BINARY, "xPos_" + i + "_" + j + "_" + k);
//                        numberOfPackages[i][j][k] = model.addVar(0, GRB.INFINITY, 0, GRB.INTEGER, "numPkg_" + i + "_" + j + "_" + k);
//                        xRemainder[i][j][k] = model.addVar(0, 1, 0, GRB.BINARY, "xRem_" + i + "_" + j + "_" + k);
                    }
                }
            }

            // Set objective function
            GRBLinExpr expr = new GRBLinExpr();
            for (int j = 0; j < numTasks; j++) {
                expr.addTerm(util[j], y[j]);  // Utility term for task j
                for (int i = 0; i < numAgents; i++) {
                    for (int k = 0; k < numResourceTypes; k++) {
                        // Subtract transfer cost
//                        expr.addConstant(-distance[i][j]);
//                        expr.addTerm(-distance[i][j], x[i][j][k]);
                        //TODO: define transfer cost per resource type - multiply -distance[i][j] by a factor
//                        expr.addTerm(-distance[i][j], numberOfPackages[i][j][k]);
//                        expr.addTerm(-distance[i][j], xRemainder[i][j][k]);
                        expr.addTerm(-distance[i][j], xPositive[i][j][k]);
                    }
                }
            }
            model.setObjective(expr, GRB.MAXIMIZE);

            // Add constraints
            for (int j = 0; j < numTasks; j++) {
                for (int k = 0; k < numResourceTypes; k++) {
                    GRBLinExpr constrExpr = new GRBLinExpr();
                    for (int i = 0; i < numAgents; i++) {
                        constrExpr.addTerm(1.0, x[i][j][k]);
                    }
                    GRBLinExpr rhsExpr = new GRBLinExpr();
                    rhsExpr.addTerm(requirement[j][k], y[j]);  // Multiply the y variable by the requirement constant
                    model.addConstr(constrExpr, GRB.GREATER_EQUAL, rhsExpr, "c1");
                }
            }

            for (int i = 0; i < numAgents; i++) {
                for (int k = 0; k < numResourceTypes; k++) {
                    GRBLinExpr constrExpr = new GRBLinExpr();
                    for (int j = 0; j < numTasks; j++) {
                        constrExpr.addTerm(1.0, x[i][j][k]);
                    }
                    model.addConstr(constrExpr, GRB.LESS_EQUAL, resource[i][k], "c2");
                }
            }

            double epsilon = 0.5; // Small positive constant

            for (int i = 0; i < numAgents; i++) {
                for (int j = 0; j < numTasks; j++) {
                    for (int k = 0; k < numResourceTypes; k++) {
                        // Add constraints linking xPositive[i][j][k] to x[i][j][k]
                        // If x[i][j][k] >= epsilon, then xPositive[i][j][k] should be 1
                        GRBLinExpr xGtEpsilonExpr = new GRBLinExpr();
                        xGtEpsilonExpr.addTerm(1.0, x[i][j][k]);
                        model.addGenConstrIndicator(xPositive[i][j][k], 1, xGtEpsilonExpr, GRB.GREATER_EQUAL, epsilon, "xPosGtEps_" + i + "_" + j + "_" + k);

                        // If x[i][j][k] == 0, then xPositive[i][j][k] should be 0
                        GRBLinExpr xEqZeroExpr = new GRBLinExpr();
                        xEqZeroExpr.addTerm(1.0, x[i][j][k]);
                        model.addGenConstrIndicator(xPositive[i][j][k], 0, xEqZeroExpr, GRB.EQUAL, 0, "xPosEq0_" + i + "_" + j + "_" + k);
                    }
                }
            }

            System.out.println("=======  Started model.optimize() ========");

            // Optimize
            model.optimize();

            // Display results
            System.out.println("Objective Value: " + model.get(GRB.DoubleAttr.ObjVal));
            for (int j = 0; j < numTasks; j++) {
                System.out.println("Task " + (j + 1) + ": " + y[j].get(GRB.DoubleAttr.X));
            }
            for (int i = 0; i < numAgents; i++) {
                for (int j = 0; j < numTasks; j++) {
                    for (int k = 0; k < numResourceTypes; k++) {
                        System.out.println("Agent " + (i + 1) + " for Task " + (j + 1) + " with Resource: " + (k + 1) + " x: " + x[i][j][k].get(GRB.DoubleAttr.X) + " xPositive: " + xPositive[i][j][k].get(GRB.DoubleAttr.X));
                    }
                }
            }

            objectiveValue = model.get(GRB.DoubleAttr.ObjVal);

            //TODO:
            // Printing the solution status after optimization. This will tell you if Gurobi found an optimal solution, an infeasible model, or something else.

            model.dispose();
            env.dispose();

        } catch (GRBException e) {
            System.out.println("Error code: " + e.getErrorCode() + ". " + e.getMessage());
            e.printStackTrace();
        }

        return objectiveValue;
    }
}

