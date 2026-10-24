using System;
using System.Collections.Generic;
using TaleWorlds.Library;

namespace MinecraftPassthrough
{
	internal static class Guard
	{
		private static readonly HashSet<string> Reported = new HashSet<string>();

		public static void Run(string where, Action action)
		{
			try
			{
				action();
			}
			catch (Exception e)
			{
				Report(where, e);
			}
		}

		public static void Report(string where, Exception e)
		{
			Debug.Print("[MinecraftPassthrough] error in " + where + ": " + e);
			if (Reported.Add(where + e.GetType().Name))
			{
				InformationManager.DisplayMessage(new InformationMessage(Text.Get("blmc_error", "Minecraft Passthrough: error in {WHERE} ({TYPE}). The details are in rgl_log.",
					"WHERE", where, "TYPE", e.GetType().Name), Colors.Red));
			}
		}
	}
}
