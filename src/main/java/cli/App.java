package cli;

import engine.WorkflowEngine;
import examples.onboarding.Employee;
import examples.onboarding.OnboardingWorkflow;

import java.util.Scanner;

public class App {
    private static final String DB_PATH = "workflow.db";
    private static final String WORKFLOW_ID = "onboarding-001";
    
    public static void main(String[] args) {
        try {
            WorkflowEngine engine = new WorkflowEngine(DB_PATH);
            Scanner scanner = new Scanner(System.in);
            
            printBanner();
            
            while (true) {
                printMenu();
                String choice = scanner.nextLine().trim();
                
                switch (choice) {
                    case "1":
                        runWorkflow(engine, false);
                        break;
                    case "2":
                        runWorkflow(engine, true);
                        break;
                    case "3":
                        resetWorkflow(engine);
                        break;
                    case "4":
                        System.out.println("\n👋 Goodbye!\n");
                        engine.close();
                        scanner.close();
                        return;
                    default:
                        System.out.println("\n❌ Invalid choice. Please try again.\n");
                }
            }
            
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void printBanner() {
        System.out.println("\n" +
            "╔══════════════════════════════════════════════════════════╗\n" +
            "║         DURABLE EXECUTION ENGINE - DEMO                  ║\n" +
            "║         Employee Onboarding Workflow                     ║\n" +
            "╚══════════════════════════════════════════════════════════╝\n");
    }
    
    private static void printMenu() {
        System.out.println("┌────────────────────────────────────────┐");
        System.out.println("│  MENU                                  │");
        System.out.println("├────────────────────────────────────────┤");
        System.out.println("│  1. Run Workflow (Normal)              │");
        System.out.println("│  2. Run Workflow (Simulate Crash)      │");
        System.out.println("│  3. Reset Workflow State               │");
        System.out.println("│  4. Exit                               │");
        System.out.println("└────────────────────────────────────────┘");
        System.out.print("\nEnter your choice: ");
    }
    
    private static void runWorkflow(WorkflowEngine engine, boolean simulateCrash) {
        try {
            System.out.println("\n" + "=".repeat(60));
            
            Employee employee = new Employee(
                "12345",
                "Jane Smith",
                "jane.smith@company.com",
                "Engineering"
            );
            
            if (simulateCrash) {
                System.out.println("⚠️  CRASH SIMULATION MODE ENABLED");
                System.out.println("The workflow will crash after 2-4 seconds.");
                System.out.println("Some steps will complete, others won't.");
                System.out.println("=".repeat(60) + "\n");
                
                // Create a volatile flag to signal crash
                final boolean[] shouldCrash = {false};
                
                // Start crash timer in background
                Thread crashTimer = new Thread(() -> {
                    try {
                        // Crash after 2-4 seconds
                        int delay = 2000 + (int)(Math.random() * 2000);
                        Thread.sleep(delay);
                        shouldCrash[0] = true;
                        
                        System.out.println("\n\n" + "!".repeat(60));
                        System.out.println("💥 SIMULATED CRASH! Process terminated unexpectedly!");
                        System.out.println("!".repeat(60) + "\n");
                        System.out.println("ℹ️  Run the app again and choose option 1 to resume.");
                        System.out.println("ℹ️  Completed steps will be skipped!\n");
                        
                        // Force exit
                        System.exit(0);
                    } catch (InterruptedException e) {
                        // Timer cancelled
                    }
                });
                crashTimer.setDaemon(true);
                crashTimer.start();
                
                // Run workflow
                try {
                    String result = engine.executeWorkflow(WORKFLOW_ID, ctx -> 
                        OnboardingWorkflow.execute(ctx, employee)
                    );
                    System.out.println("\n✅ " + result);
                    
                    // Cancel crash timer if workflow completes
                    crashTimer.interrupt();
                    System.out.println("\n⚠️  Workflow completed before crash could occur.");
                    System.out.println("Try option 3 to reset, then option 2 again.\n");
                } catch (Exception e) {
                    throw e;
                }
                
            } else {
                System.out.println("▶️  Running workflow normally...");
                System.out.println("=".repeat(60) + "\n");
                
                String result = engine.executeWorkflow(WORKFLOW_ID, ctx -> 
                    OnboardingWorkflow.execute(ctx, employee)
                );
                
                System.out.println("\n✅ " + result);
            }
            
            System.out.println("\n" + "=".repeat(60) + "\n");
            
        } catch (Exception e) {
            System.err.println("\n❌ Error running workflow: " + e.getMessage());
            e.printStackTrace();
            System.out.println();
        }
    }
    
    private static void resetWorkflow(WorkflowEngine engine) {
        try {
            System.out.println("\n🔄 Resetting workflow state...");
            engine.resetWorkflow(WORKFLOW_ID);
            System.out.println("✅ Workflow state cleared. You can now start fresh.\n");
        } catch (Exception e) {
            System.err.println("❌ Error resetting workflow: " + e.getMessage());
            System.out.println();
        }
    }
}