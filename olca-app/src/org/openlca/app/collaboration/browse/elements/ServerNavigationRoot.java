package org.openlca.app.collaboration.browse.elements;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.PlatformObject;
import org.openlca.app.collaboration.navigation.ServerConfigurations;

/**
 * The root of the server navigation tree
 */
public class ServerNavigationRoot extends PlatformObject implements IServerNavigationElement<ServerNavigationRoot> {

	@Override
	public ServerNavigationRoot getContent() {
		return this;
	}

	@Override
	public IServerNavigationElement<?> getParent() {
		return null;
	}

	@Override
	public void update() {
	}

	@Override
	public List<IServerNavigationElement<?>> getChildren() {
		return ServerConfigurations.get().stream()
				.map(config -> new ServerElement(this, config))
				.collect(Collectors.toList());
	}

}
