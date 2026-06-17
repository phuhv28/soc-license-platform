function App() {
  return (
    <main style={{ padding: 24, fontFamily: "Arial, sans-serif" }}>
      <h1>SOC EPS License Dashboard</h1>

      <p>Frontend is running.</p>

      <section>
        <h2>Services</h2>

        <ul>
          <li>Management API: http://localhost:8080</li>
          <li>Collector Service: http://localhost:8081</li>
          <li>PostgreSQL: localhost:5432</li>
          <li>Redis: localhost:6379</li>
        </ul>
      </section>

      <section>
        <h2>Project Scope</h2>

        <ul>
          <li>Control Plane: management-api-service</li>
          <li>Data Plane: collector-service</li>
          <li>Input Mock: event-producer</li>
        </ul>
      </section>
    </main>
  );
}

export default App;