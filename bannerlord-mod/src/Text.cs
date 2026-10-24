using TaleWorlds.Localization;

namespace MinecraftPassthrough
{
	internal static class Text
	{
		public static string Get(string id, string english, params object[] variables)
		{
			var text = new TextObject("{=" + id + "}" + english);
			for (int i = 0; i + 1 < variables.Length; i += 2)
			{
				text.SetTextVariable((string)variables[i], variables[i + 1]?.ToString() ?? "");
			}
			return text.ToString();
		}
	}
}
