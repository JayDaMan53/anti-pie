package jaydon.antipie;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AntiPie implements ModInitializer {
	public static final String MOD_ID = "anti-pie";
	public static final Logger LOGGER = LoggerFactory.getLogger("Anti Pie");

	@Override
	public void onInitialize() {
		AntiPieConfig.load();
		LOGGER.info("Anti Pie is active");
	}
}
