package model;

import gurobi.*;

public class GurobiOptimizer {

    // Define parameters
    int numAgents;  // m
    int numTasks;  // n
    int numResourceTypes;  // l

    double[] util;  // Utility for each task
    double[][] distance;  // Distance between each agent and task location
    double[][] requirement;  // Resource requirement of each task for each resource type
    double[][] resource;  // Resources of each type available to each agent

    public GurobiOptimizer(int numAgents, int numTasks, int numResourceTypes, double[] util, double[][] distance, double[][] requirement, double[][] resource) {
        this.numAgents = numAgents;
        this.numTasks = numTasks;
        this.numResourceTypes = numResourceTypes;
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
            for (int i = 0; i < numAgents; i++) {
                for (int j = 0; j < numTasks; j++) {
                    for (int k = 0; k < numResourceTypes; k++) {
//                        x[i][j][k] = model.addVar(0.0, resource[i][k], 0.0, GRB.INTEGER, "x_" + i + "_" + j + "_" + k);
                        x[i][j][k] = model.addVar(0.0, requirement[j][k], 0.0, GRB.INTEGER, "x_" + i + "_" + j + "_" + k);
                    }
                }
            }

            // Set objective function
            GRBLinExpr expr = new GRBLinExpr();
            for (int j = 0; j < numTasks; j++) {
                expr.addTerm(util[j], y[j]);  // Utility term for task j
                for (int i = 0; i < numAgents; i++) {
                    for (int k = 0; k < numResourceTypes; k++) {
                        expr.addTerm(-distance[i][j], x[i][j][k]);  // Negative distance term for task j from agent i only if resources are supplied by agent i
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

            System.out.println("=======  Started model.optimize() ========");

            // Optimize
            model.optimize();

            // Display results
            System.out.println("Objective Value: " + model.get(GRB.DoubleAttr.ObjVal));
            for (int j = 0; j < numTasks; j++) {
                System.out.println("Task " + (j + 1) + ": " + y[j].get(GRB.DoubleAttr.X));
            }
//            for (int i = 0; i < numAgents; i++) {
//                for (int j = 0; j < numTasks; j++) {
//                    for (int k = 0; k < numResourceTypes; k++) {
//                        System.out.println("Agent " + (i + 1) + " for Task " + (j + 1) + " with Resource " + (k + 1) + ": " + x[i][j][k].get(GRB.DoubleAttr.X));
//                    }
//                }
//            }

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

