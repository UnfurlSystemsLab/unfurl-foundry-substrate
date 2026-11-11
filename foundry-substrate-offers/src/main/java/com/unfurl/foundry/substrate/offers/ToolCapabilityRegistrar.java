package com.unfurl.foundry.substrate.offers;

import com.unfurl.dcp.spi.CapabilityRegistrar;
import com.unfurl.foundry.substrate.ports.ToolExecutor;
import com.unfurl.foundry.substrate.tool.ToolDefinition;
import com.unfurl.substrate.policy.ExecutionContext;

import java.util.List;
import java.util.Optional;

/**
 * Registrar shape (Ports &amp; Adapters): a DCP {@link CapabilityRegistrar} whose accepted registrations an agent host
 * can expose as tools (LLD, <em>Tool registrar shape</em>). It is the meeting point for an in-process DCP component and
 * an agent host: DCP's in-process component host asks the component's activation for a registrar of this shape (DCP
 * HLD-C §6.1), the broker registers each accepted operation through it, and the host reads the resulting tools from it.
 * The component therefore never depends on a host product, and the host never imports component types.
 *
 * <p>Contract for implementations, which components supply:
 * <ul>
 *   <li>Nothing is visible before the broker's {@code register}: definitions and executors exist only for accepted
 *       operations.</li>
 *   <li>Both reads are scoped to the context's tenant; another tenant sees nothing.</li>
 *   <li>Each {@link ToolDefinition} carries the frozen contract id and version, and the component's own required
 *       permission, in its metadata.</li>
 *   <li>{@code unregister} fences the handle and finishes an active call before returning, so revocation through the
 *       broker disables the tool; a retained executor then fails closed. {@code unregister} is idempotent.</li>
 * </ul>
 */
public interface ToolCapabilityRegistrar extends CapabilityRegistrar {
    /**
     * Returns the definitions of the accepted operations for the context's tenant only.
     *
     * @param context the caller's trusted context
     * @return the definitions, empty before any acceptance or for a tenant with none
     */
    List<ToolDefinition> definitions(ExecutionContext context);

    /**
     * Returns the accepted handle for a tool name in the context's tenant.
     *
     * @param toolName the tool's name, as its definition gives it
     * @param context  the caller's trusted context
     * @return the executor, or empty when no accepted operation has that name for the tenant; a revoked handle fails closed
     */
    Optional<ToolExecutor> executor(String toolName, ExecutionContext context);
}
