package examples.onboarding;

import engine.DurableContext;
import engine.WorkflowEngine;

public class OnboardingWorkflow {
    
    public static String execute(DurableContext ctx, Employee employee) throws Exception {
        System.out.println("\n=== Starting Employee Onboarding ===");
        System.out.println("Employee: " + employee.getName());
        System.out.println("Email: " + employee.getEmail());
        System.out.println("Department: " + employee.getDepartment());
        System.out.println("=====================================\n");
        
        String recordId = ctx.step("create-record", () -> {
            System.out.println("📝 Creating employee record...");
            simulateWork(2000);
            String id = "EMP-" + System.currentTimeMillis();
            System.out.println("✅ Employee record created: " + id);
            return id;
        });
        
        System.out.println("\n--- Starting Parallel Provisioning ---\n");
        
        WorkflowEngine.ParallelExecutor parallel = new WorkflowEngine.ParallelExecutor(ctx);
        
        var laptopFuture = parallel.submit("provision-laptop", () -> {
            System.out.println("💻 Provisioning laptop...");
            simulateWork(3000);
            String laptopId = "LAPTOP-" + employee.getId();
            System.out.println("✅ Laptop provisioned: " + laptopId);
            return laptopId;
        });
        
        var accessFuture = parallel.submit("setup-access", () -> {
            System.out.println("🔑 Setting up access credentials...");
            simulateWork(2500);
            String username = employee.getName().toLowerCase().replace(" ", ".");
            System.out.println("✅ Access credentials created: " + username);
            return username;
        });
        
        parallel.await();
        
        String laptopId = laptopFuture.get();
        String username = accessFuture.get();
        
        System.out.println("\n--- Parallel Provisioning Complete ---\n");
        
        String emailStatus = ctx.step("send-welcome-email", () -> {
            System.out.println("📧 Sending welcome email to " + employee.getEmail() + "...");
            simulateWork(1500);
            System.out.println("✅ Welcome email sent successfully");
            return "EMAIL_SENT";
        });
        
        System.out.println("\n=== Onboarding Complete ===");
        System.out.println("Record ID: " + recordId);
        System.out.println("Laptop: " + laptopId);
        System.out.println("Username: " + username);
        System.out.println("Email Status: " + emailStatus);
        System.out.println("===========================\n");
        
        return "Onboarding completed for " + employee.getName();
    }
    
    private static void simulateWork(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Work interrupted", e);
        }
    }
}