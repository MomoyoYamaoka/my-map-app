import React, { useState, useEffect} from "react";
import { GoogleMap, LoadScript, Polyline } from 
"@react-google-maps/api";
import {
  Home,
  Mail,
  Tag,
  Settings,
  Footprints,
  Search
} from 'lucide-react';


function App() {
  const [isContactOpen, setIsContactOpen] = useState(false);
  const [language, setLanguage] = useState("English");
  const [isSettingOpen, setIsSettingOpen] = useState(false);
  const [initialPosition, setInitialPosition] = useState("Seattle");
  const [darkMode, setDarkMode] = useState(false);
  const [search, setSearch] = useState("");
  const [coords, setCoords] = useState([47.6062, -122.3321]);
  /**const [message, setMessage] = useState("");*/
  const [streets, setStreets] = useState([]);

  useEffect(() => {
    fetch("https://safety-map-backend.onrender.com/api/streets")
      .then((res) => res.json())
      .then((data) => {
        console.log("Street data:", data); 
        setStreets(data);
      })
      .catch(err => console.error("Error fetching street data:", err));
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
    <div className={`${darkMode ? 'dark' : ''}`}>
      <div className="h-screen w-screen flex flex-col font-sans">
        {/* 上部バー */}
        <div className="bg-white shadow-md p-4 flex items-center space-x-3">
          <Footprints className="text-blue-700 w-6 h-6" />
          <h1 className="text-xl font-poppins text-blue-900">
            <span className="font-normal">Her</span>
            <span className="font-bold">Route</span>
          </h1>
        </div>

        {/* 本体レイアウト */}
        <div className="flex flex-1">
          {/* サイドバー */}
          <aside className="bg-blue-700 text-white w-16 flex flex-col items-center py-4 space-y-6">
            <Home className="w-6 h-6 cursor-pointer"
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

          {/* コンテンツ部分（全体） */}
          <main className="flex-1 bg-blue-100 dark:bg-gray-900 p-6 overflow-hidden flex flex-col">
          {/*<p className={darkMode ? "text-white" : "text-gray-800"}>
            {message}
          </p>*/}
            {/* タイトル + 検索：設定画面 or 問い合わせ画面のときは非表示 */}
            {!isSettingOpen && !isContactOpen && (
              <div className="flex flex-col md:flex-row justify-between items-start md:items-center mb-4 space-y-4 md:space-y-0 md:space-x-4">
                <div className="flex justify-between items-center mb-4">
                  <div className="flex items-center space-x-4">
                    {/* Toggle */}
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
                            darkMode ? "translate-x-5 bg-blue-400" : "translate-x-0"
                          }`}
                        ></div>
                      </div>
                    </label>
                    <h2
                      className={`text-xl font-semibold ${
                        darkMode ? 'text-white' : 'text-gray-800'
                      }`}
                    >
                      {language === "English"
                        ? darkMode
                          ? 'Where are you going at night?'
                          : 'Where are you going during the day?'
                        : darkMode
                          ? '夜はどこ行く？'
                          : 'お昼はどこ行く？'}
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

          {/*
          {!isSettingOpen && !isContactOpen && streets.length > 0 && (
            <div className="bg-white rounded p-2 mb-4 max-h-64 overflow-auto text-sm shadow-inner">
              <h2 className="font-bold mb-2">Street Scores</h2>
              {streets.map((s, idx) => (
                <div key={idx} className="mb-1">
                  {s.streetName} - Score: {s.averageCrimeScore.toFixed(2)} - 色: {s.color}
                </div>
              ))}
            </div>
          )}
          */}

            {/* 表示切り替え：地図 or 設定画面 */}
            {isSettingOpen ? (
              <div className="space-y-4">
                {/* 初期位置の変更 */}
                <h2 className={`text-xl font-bold ${darkMode ? 'text-white' : 'text-gray-800'}`}>
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

                {/* 言語設定 */}
                <h2 className={`text-xl font-bold ${darkMode ? 'text-white' : 'text-gray-800'}`}>
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
                <h2 className={`text-xl font-bold ${darkMode ? 'text-white' : 'text-gray-800'}`}>
                  {language === "日本語" ? "お名前" : "Name"}
                </h2>
                <input
                  type="text"
                  className="w-full px-4 py-2 rounded border"
                />

                <h2 className={`text-xl font-bold ${darkMode ? 'text-white' : 'text-gray-800'}`}>
                  {language === "日本語" ? "メールアドレス" : "Email address"}
                </h2>
                <input
                  type="email"
                  className="w-full px-4 py-2 rounded border"
                />

                <h2 className={`text-xl font-bold ${darkMode ? 'text-white' : 'text-gray-800'}`}>
                  {language === "日本語" ? "お問い合わせ内容" : "Message"}
                </h2>
                <textarea
                  rows={6}
                  maxLength={200}
                  className="w-full px-4 py-2 rounded border"
                ></textarea>
              </div>
            ) : (
              <div className="flex-1 rounded overflow-hidden">
                <MyMap coords={coords} language={language} streets={streets}/>
              </div>
            )}
          </main>
        </div>
      </div>
    </div>
  );
}

export default App;

function MyMap({coords, language, streets}) {
  const mapRef = React.useRef(null);

  React.useEffect(() => {
    if (mapRef.current && coords) {
      mapRef.current.panTo({ lat: coords[0], lng: coords[1] });
    }
  }, [coords]);

  return (
    <LoadScript googleMapsApiKey="AIzaSyCans_CRdAN8_NSX5-kYpgxkAdPfyUV_7c"
      language={language === "日本語" ? "ja" : "en"}
    >
      <GoogleMap
        mapContainerStyle={{ width: "100%", height: "100%" }}
        center={{ lat: coords[0], lng: coords[1] }}
        zoom={13}
        onLoad={(map) => (mapRef.current = map)}
      >
        {streets.map((street, idx) => (
          <Polyline
            key={idx}
            path={street.coordinates.map(p => ({
              lat: p.latitude,
              lng: p.longitude
            }))}
            options={{
              strokeColor: street.color,
              strokeOpacity: 0.1,
              strokeWeight: 6
            }}
          />
        ))}
      </GoogleMap>
    </LoadScript>
  );
}