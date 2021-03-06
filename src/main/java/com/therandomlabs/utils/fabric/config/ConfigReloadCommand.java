/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018-2019 TheRandomLabs
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.therandomlabs.utils.fabric.config;

import java.util.function.Consumer;

import com.google.common.base.Preconditions;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.therandomlabs.utils.config.ConfigManager;
import com.therandomlabs.utils.fabric.FabricUtils;
import io.github.cottonmc.clientcommands.CottonClientCommandSource;
import net.minecraft.server.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a command that reloads a TRLUtils-Config configuration.
 */
public final class ConfigReloadCommand {
	private final String name;
	private final String clientName;

	private final Class<?> configClass;

	@Nullable
	private Consumer<? super CommandSource> preReload;
	@Nullable
	private Consumer<? super CommandSource> postReload;

	@Nullable
	private String serverSuccessMessage;

	/**
	 * Creates a {@link ConfigReloadCommand} with the specified name, client-sided name
	 * and TRLUtils-Config configuration class.
	 *
	 * @param name a server-sided command name.
	 * @param clientName a client-sided command name.
	 * @param configClass a TRLUtils-Config configuration class.
	 */
	public ConfigReloadCommand(String name, String clientName, Class<?> configClass) {
		Preconditions.checkNotNull(name, "name should not be null");
		Preconditions.checkNotNull(clientName, "clientName should not be null");
		Preconditions.checkNotNull(configClass, "configClass should not be null");
		this.name = name;
		this.clientName = clientName;
		this.configClass = configClass;
	}

	/**
	 * Sets the {@link Consumer} that is called just before the configuration is reloaded.
	 *
	 * @param preReload a {@link Consumer} that accepts a {@link CommandSource}.
	 * @return this {@link ConfigReloadCommand}.
	 */
	public ConfigReloadCommand preReload(Consumer<? super CommandSource> preReload) {
		Preconditions.checkNotNull(preReload, "preReload should not be null");

		if (this.preReload != null) {
			throw new IllegalStateException("preReload has already been set");
		}

		this.preReload = preReload;
		return this;
	}

	/**
	 * Sets the {@link Consumer} that is called just after the configuration is reloaded.
	 *
	 * @param postReload a {@link Consumer} that accepts a {@link CommandSource}.
	 * @return this {@link ConfigReloadCommand}.
	 */
	public ConfigReloadCommand postReload(Consumer<? super CommandSource> postReload) {
		Preconditions.checkNotNull(postReload, "postReload should not be null");

		if (this.preReload != null) {
			throw new IllegalStateException("postReload has already been set");
		}

		this.postReload = postReload;
		return this;
	}

	/**
	 * Sets the server-sided success message. This message is only used on dedicated servers
	 * where the localization files cannot be accessed.
	 *
	 * @param message a server-sided success message.
	 * @return this {@link ConfigReloadCommand}.
	 */
	public ConfigReloadCommand serverSuccessMessage(String message) {
		Preconditions.checkNotNull(message, "message should not be null");

		if (serverSuccessMessage != null) {
			throw new IllegalStateException("serverSuccessMessage has already been set");
		}

		serverSuccessMessage = message;
		return this;
	}

	/**
	 * Registers the client-sided version of this command to the specified
	 * {@link CommandDispatcher}.
	 *
	 * @param dispatcher a {@link CommandDispatcher}.
	 */
	public void registerClient(CommandDispatcher<? extends CommandSource> dispatcher) {
		Preconditions.checkNotNull(dispatcher, "dispatcher should not be null");
		register(dispatcher, clientName, 0);
	}

	/**
	 * Registers the server-sided version of this command to the specified
	 * {@link CommandDispatcher}.
	 *
	 * @param dispatcher a {@link CommandDispatcher}.
	 */
	public void registerServer(CommandDispatcher<? extends CommandSource> dispatcher) {
		Preconditions.checkNotNull(dispatcher, "dispatcher should not be null");
		register(dispatcher, name, 4);
	}

	@SuppressWarnings("unchecked")
	private void register(
			CommandDispatcher<? extends CommandSource> dispatcher, String name, int permissionLevel
	) {
		((CommandDispatcher<CommandSource>) dispatcher).register(
				LiteralArgumentBuilder.<CommandSource>literal(name).
						requires(source -> source.hasPermissionLevel(permissionLevel)).
						executes(this::execute)
		);
	}

	private int execute(CommandContext<CommandSource> context) {
		final CommandSource source = context.getSource();

		if (preReload != null) {
			preReload.accept(source);
		}

		ConfigManager.reloadFromDisk(configClass);

		if (postReload != null) {
			postReload.accept(source);
		}

		final boolean dedicatedServer = FabricUtils.isDedicatedServer(source);

		//Assume the source is a ServerCommandSource for now
		if (serverSuccessMessage != null && dedicatedServer) {
			((ServerCommandSource) source).sendFeedback(
					new LiteralText(serverSuccessMessage), true
			);
		} else {
			final String currentName = dedicatedServer ? name : clientName;
			final Text text = new TranslatableText("commands." + currentName + ".success");

			if (source instanceof ServerCommandSource) {
				((ServerCommandSource) source).sendFeedback(text, true);
			} else {
				((CottonClientCommandSource) source).sendFeedback(text);
			}
		}

		return Command.SINGLE_SUCCESS;
	}
}
