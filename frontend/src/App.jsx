import React, { useEffect, useState } from "react";
import { GoogleMap, LoadScript, Polyline } from "@react-google-maps/api";
import { Footprints, Home, Mail, Search, Settings, Tag } from "lucide-react";

function App() {
  const [isContactOpen, setIsContactOpen] = useState(false);
  const [language, setLanguage] = useState("English");
  const [isSettingOpen, setIsSettingOpen] = useState(false);
  const [initialPosition, setInitialPosition] = useState("Seattle");
  const [darkMode, setDarkMode] = useState(false);
  const [search, setSearch] = useState("");
  const [coords, setCoords] = useState([47.6062, -122.3321]);
  const [streets, setStreets] = useState([]);

  useEffect(() => {
    // 別媒体（別ドメイン・iframe・別サーバ）から開いた場合、同じオリジンでないと /api/streets が届かない。
    // 実行時は window.__API_BASE_URL__ を優先（埋め込み時に <script>window.__API_BASE_URL__='https://...';</script> で指定可能）。
    const apiBaseUrl =
      typeof window !== "undefined" && window.__API_BASE_URL__ != null
        ? String(window.__API_BASE_URL__).replace(/\/$/, "")
        : (import.meta.env.VITE_API_BASE_URL ?? (import.meta.env.DEV ? "http://localhost:8080" : ""));
    const url = apiBaseUrl ? `${apiBaseUrl}/api/streets` : "/api/streets";
    fetch(url)
      .then((res) => {
        if (!res.ok) throw new Error(`API ${res.status}: ${url}`);
        return res.json();
      })
      .then((data) => {
        console.log("Street data:", data?.length ?? 0, "segments");
        setStreets(Array.isArray(data) ? data : []);
      })
      .catch((err) => {
        console.error("Error fetching street data:", err);
        setStreets([]);
      });
  }, []);

  const handleSearch = async (e) => {
    if (e.key === "Enter") {
      const response = await fetch(
        `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(search)}`
      );
      const data = await response.json();
      if (data && data.length > 0) {
        const lat = parseFloat(data[0].lat);
        const lon = parseFloat(data[0].lon);
        setCoords([lat, lon]);
      } else {
        alert("場所が見つかりませんでした！");
      }
    }
  };

  return (
    <div className={`${darkMode ? "dark" : ""}`}>
      <div className="h-screen w-screen flex flex-col font-sans">
        <div className="bg-white shadow-md p-4 flex items-center space-x-3">
          <Footprints className="w-6 h-6 text-[#D04088]" />
          <h1 className="text-xl font-poppins text-[#D04088]">
            <span className="font-normal">Her</span>
            <span className="font-bold">Route</span>
          </h1>
        </div>

        <div className="flex flex-1">
          <aside className="bg-[#CD4187] text-white w-16 flex flex-col items-center py-4 space-y-6">
            <Home
              className="w-6 h-6 cursor-pointer"
              onClick={() => {
                setIsSettingOpen(false);
                setIsContactOpen(false);
              }}
            />
            <Tag className="w-6 h-6" />
            <Mail
              className="w-6 h-6 cursor-pointer"
              onClick={() => {
                setIsSettingOpen(false);
                setIsContactOpen(true);
              }}
            />
            <Settings
              className="w-6 h-6 cursor-pointer"
              onClick={() => {
                setIsSettingOpen(!isSettingOpen);
                setIsContactOpen(false);
              }}
            />
          </aside>

          <main className="flex-1 bg-[#f5f2f3] dark:bg-gray-900 p-6 overflow-hidden flex flex-col">
            {!isSettingOpen && !isContactOpen && (
              <div className="flex flex-col md:flex-row justify-between items-start md:items-center mb-4 space-y-4 md:space-y-0 md:space-x-4">
                <div className="flex justify-between items-center mb-4">
                  <div className="flex items-center space-x-4">
                    <label className="flex items-center cursor-pointer">
                      <input
                        type="checkbox"
                        className="sr-only"
                        checked={darkMode}
                        onChange={() => setDarkMode(!darkMode)}
                      />
                      <div className="w-10 h-5 bg-gray-300 dark:bg-gray-600 rounded-full relative">
                        <div
                          className={`absolute w-5 h-5 bg-white rounded-full shadow transition transform ${
                            darkMode ? "translate-x-5 bg-pink-400" : "translate-x-0"
                          }`}
                        />
                      </div>
                    </label>
                    <h2 className={`text-xl font-semibold ${darkMode ? "text-white" : "text-gray-800"}`}>
                      {language === "English"
                        ? darkMode
                          ? "Where are you going at night?"
                          : "Where are you going during the day?"
                        : darkMode
                          ? "夜はどこ行く？"
                          : "お昼はどこ行く？"}
                    </h2>
                  </div>

                  <div className="relative w-80">
                    <Search className="absolute top-2.5 left-3 w-4 h-4 text-gray-400" />
                    <input
                      type="text"
                      value={search}
                      onChange={(e) => setSearch(e.target.value)}
                      onKeyDown={handleSearch}
                      placeholder="Search location..."
                      className="w-full pl-10 pr-4 py-2 rounded border focus:outline-none"
                    />
                  </div>
                </div>
              </div>
            )}

            {isSettingOpen ? (
              <div className="space-y-4">
                <h2 className={`text-xl font-bold ${darkMode ? "text-white" : "text-gray-800"}`}>
                  {language === "日本語" ? "初期位置設定" : "Change of initial position"}
                </h2>
                <select
                  value={initialPosition}
                  onChange={(e) => {
                    const val = e.target.value;
                    setInitialPosition(val);
                    if (val === "Seattle") {
                      setCoords([47.6062, -122.3321]);
                    } else if (val === "Tokyo") {
                      setCoords([35.6812, 139.7671]);
                    }
                  }}
                  className="text-black px-4 py-2 rounded"
                >
                  <option value="Seattle">Seattle</option>
                  <option value="Tokyo">Tokyo</option>
                </select>

                <h2 className={`text-xl font-bold ${darkMode ? "text-white" : "text-gray-800"}`}>
                  {language === "日本語" ? "言語設定" : "Language settings"}
                </h2>
                <select
                  value={language}
                  onChange={(e) => setLanguage(e.target.value)}
                  className="text-black px-4 py-2 rounded"
                >
                  <option value="English">English</option>
                  <option value="日本語">日本語</option>
                </select>
              </div>
            ) : isContactOpen ? (
              <div className="space-y-4">
                <h2 className={`text-xl font-bold ${darkMode ? "text-white" : "text-gray-800"}`}>
                  {language === "日本語" ? "お名前" : "Name"}
                </h2>
                <input type="text" className="w-full px-4 py-2 rounded border" />

                <h2 className={`text-xl font-bold ${darkMode ? "text-white" : "text-gray-800"}`}>
                  {language === "日本語" ? "メールアドレス" : "Email address"}
                </h2>
                <input type="email" className="w-full px-4 py-2 rounded border" />

                <h2 className={`text-xl font-bold ${darkMode ? "text-white" : "text-gray-800"}`}>
                  {language === "日本語" ? "お問い合わせ内容" : "Message"}
                </h2>
                <textarea rows={6} maxLength={200} className="w-full px-4 py-2 rounded border" />
              </div>
            ) : (
              <div className="flex-1 flex flex-col rounded overflow-hidden">
                <div className="flex-shrink-0 px-3 py-2 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 rounded-t flex items-center gap-2">
                  <span className="text-base font-semibold text-gray-800 dark:text-gray-200">道路データ:</span>
                  <span className="text-lg font-bold text-[#D04088]">{Array.isArray(streets) ? streets.length : 0}</span>
                  <span className="text-base text-gray-600 dark:text-gray-400">本</span>
                  {Array.isArray(streets) && streets.length === 0 && (
                    <span className="ml-2 text-sm text-amber-600">（取得中またはAPI未接続）</span>
                  )}
                </div>
                <div className="flex-1 min-h-0 rounded-b overflow-hidden">
                  <MyMap coords={coords} language={language} streets={streets} />
                </div>
              </div>
            )}
          </main>
        </div>
      </div>
    </div>
  );
}

export default App;

function MyMap({ coords, language, streets }) {
  const mapRef = React.useRef(null);
  const [mapReady, setMapReady] = React.useState(false);
  const googleMapsApiKey =
    import.meta.env.VITE_GOOGLE_MAPS_API_KEY ||
    "AIzaSyCans_CRdAN8_NSX5-kYpgxkAdPfyUV_7c";

  React.useEffect(() => {
    if (mapRef.current && coords) {
      mapRef.current.panTo({ lat: coords[0], lng: coords[1] });
    }
  }, [coords]);

  const safeStreets = Array.isArray(streets) ? streets : [];
  const paths = safeStreets.filter(
    (s) => s && s.coordinates && Array.isArray(s.coordinates) && s.coordinates.length >= 2
  );

  return (
    <LoadScript googleMapsApiKey={googleMapsApiKey} language={language === "日本語" ? "ja" : "en"}>
      <GoogleMap
        mapContainerStyle={{ width: "100%", height: "100%" }}
        center={{ lat: coords[0], lng: coords[1] }}
        zoom={13}
        onLoad={(map) => {
          mapRef.current = map;
          setMapReady(true);
        }}
      >
        {mapReady && paths.map((street, idx) => (
          <Polyline
            key={street.streetId || idx}
            path={street.coordinates.map((p) => ({
              lat: p.latitude ?? p.lat,
              lng: p.longitude ?? p.lng,
            }))}
            options={{
              strokeColor: street.color || "#b22222",
              strokeOpacity: 1,
              strokeWeight: 14,
            }}
          />
        ))}
      </GoogleMap>
    </LoadScript>
  );
}

