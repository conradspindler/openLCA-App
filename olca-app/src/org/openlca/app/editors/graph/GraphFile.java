package org.openlca.app.editors.graph;

import java.io.File;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.openlca.app.db.DatabaseDir;
import org.openlca.app.editors.graph.layouts.NodeLayoutInfo;
import org.openlca.app.editors.graph.model.Node;
import org.openlca.core.model.ProductSystem;
import org.openlca.jsonld.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * We save the current layout and some settings in an external file of the
 * database folder.
 */
public final class GraphFile {

	private GraphFile() {
	}

	public static void save(GraphEditor editor) {
		var root = editor != null
				? editor.getModel()
				: null;
		if (root == null)
			return;
		try {

			// add config
			var rootObj = new JsonObject();
			rootObj.add("config", editor.config.toJson());

			// add node infos
			var file = file(editor.getProductSystem());
			var nodeArray = new JsonArray();
			for (var node : root.getChildren()) {
				var nodeObj = toJson(node);
				if (nodeObj != null) {
					nodeArray.add(nodeObj);
				}
			}
			rootObj.add("nodes", nodeArray);

			Json.write(rootObj, file);
		} catch (Exception e) {
			Logger log = LoggerFactory.getLogger(GraphFile.class);
			log.error("Failed to save layout", e);
		}
	}

	private static JsonObject toJson(Node node) {
		if (node == null || node.descriptor == null)
			return null;
		var json = new JsonObject();
		json.addProperty("id", node.descriptor.refId);

		var size = node.getSize();
		if (size != null) {
			json.addProperty("width",  size.width);
			json.addProperty("height",  size.height);
		}
		var location = node.getLocation();
		if (location != null) {
			json.addProperty("x", location.x);
			json.addProperty("y",  location.y);
		}

		json.addProperty("minimized", node.isMinimized());
		// TODO
		//		json.addProperty("expandedLeft", node.isExpandedLeft());
		//		json.addProperty("expandedRight", node.isExpandedLeft());
		//		json.addProperty("marked", node.isMarked());

		return json;
	}

	public static GraphConfig getGraphConfig(GraphEditor editor) {
		try {
			var file = GraphFile.file(editor.getProductSystem());
			if (!file.exists())
				return null;
			var rootObj = Json.readObject(file)
				.orElse(null);
			if (rootObj == null)
				return null;

			// apply graph config
			return GraphConfig.fromJson(
				Json.getObject(rootObj, "config"));
		} catch (Exception e) {
			var log = LoggerFactory.getLogger(GraphFile.class);
			log.error("Failed to load config", e);
			return null;
		}
	}

	public static JsonArray getLayouts(GraphEditor editor) {
		try {
			// read JSON object from file
			var file = file(editor.getProductSystem());
			if (!file.exists())
				return null;
			var rootObj = Json.readObject(file).orElse(null);
			if (rootObj == null)
				return null;

			// apply node info's
			return Json.getArray(rootObj, "nodes");
		} catch (Exception e) {
			var log = LoggerFactory.getLogger(GraphFile.class);
			log.error("Failed to load layout", e);
			return null;
		}
	}

	public static NodeLayoutInfo toInfo(JsonObject obj) {
		if (obj == null)
			return null;
		var info = new NodeLayoutInfo();
		info.id = Json.getString(obj, "id");
		info.box.x = Json.getInt(obj, "x", 0);
		info.box.y = Json.getInt(obj, "y", 0);
		info.box.width = Json.getInt(obj, "width", 175);
		info.box.height = Json.getInt(obj, "height", 25);
		info.minimized = Json.getBool(obj, "minimized", false);
		info.expandedLeft = Json.getBool(obj, "expandedLeft", false);
		info.expandedRight = Json.getBool(obj, "expandedRight", false);
		return info;
	}

	public static File file(ProductSystem system) {
		File dir = DatabaseDir.getDir(system);
		if (!dir.exists()) {
			if (!dir.mkdirs()) {
				throw new RuntimeException(
						"failed to create folder " + dir);
			}
		}
		return new File(dir, "layout.json");
	}

}
